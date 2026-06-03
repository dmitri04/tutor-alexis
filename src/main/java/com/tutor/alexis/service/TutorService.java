package com.tutor.alexis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutor.alexis.config.SystemPromptConfig;
import com.tutor.alexis.model.*;
import com.tutor.alexis.model.ExamenResultado;
import com.tutor.alexis.repository.ExamenResultadoRepository;
import com.tutor.alexis.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TutorService {

    @Autowired private ClaudeService claudeService;
    @Autowired private SystemPromptConfig systemPromptConfig;
    @Autowired private SesionRepository sesionRepository;
    @Autowired private MensajeRepository mensajeRepository;
    @Autowired private PerfilEstudianteRepository perfilRepository;
    @Autowired private EmailService emailService;
    @Autowired private ObjetivoEstudioRepository objetivoRepository;
    @Autowired private LeccionCompletadaRepository leccionRepository;
    @Autowired private ExamenResultadoRepository examenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Long sesionActivaId = null;
    private List<Map<String, Object>> historialActivo = new ArrayList<>();
    private LocalDateTime inicioSesion = null;
    private int erroresConsecutivos = 0;

    public String procesarMensaje(String mensajeUsuario) {
        return procesarMensajeConImagen(mensajeUsuario, null, null);
    }

    public String procesarMensajeConImagen(String mensajeUsuario, String imagenBase64, String mediaType) {
        if (sesionActivaId == null) {
            iniciarNuevaSesion();
        }

        Object contenidoMensaje;
        if (imagenBase64 != null) {
            contenidoMensaje = List.of(
                    Map.of("type", "image", "source", Map.of(
                            "type", "base64",
                            "media_type", mediaType,
                            "data", imagenBase64
                    )),
                    Map.of("type", "text", "text", mensajeUsuario)
            );
        } else {
            contenidoMensaje = mensajeUsuario;
        }

        guardarMensaje(sesionActivaId, "user", mensajeUsuario);
        historialActivo.add(Map.of("role", "user", "content", contenidoMensaje));

        String systemPrompt = obtenerSystemPrompt();

        List<Map<String, Object>> historialRecortado = historialActivo.size() > 40
                ? new ArrayList<>(historialActivo.subList(historialActivo.size() - 40, historialActivo.size()))
                : historialActivo;

        String respuesta = claudeService.enviarConversacionCompleta(systemPrompt, historialRecortado);

        // Detectar errores de conexión
        if (respuesta.contains("Sin conexión") || respuesta.contains("Failed to resolve")) {
            erroresConsecutivos++;
            System.err.println("🚨 ERROR CONEXIÓN #" + erroresConsecutivos + " - Alexis no puede estudiar");
        } else {
            erroresConsecutivos = 0;
        }

        guardarMensaje(sesionActivaId, "assistant", respuesta);
        historialActivo.add(Map.of("role", "assistant", "content", respuesta));

        // Persistir historial en BD después de cada mensaje
        persistirHistorial();

        procesarBloquesDiagnostico(respuesta);
        procesarBloqueReporte(respuesta);
        procesarBloqueExamen(respuesta);

        return respuesta;
    }

    private void persistirHistorial() {
        try {
            List<Map<String, Object>> historialParaGuardar = historialActivo.size() > 40
                    ? new ArrayList<>(historialActivo.subList(
                    historialActivo.size() - 40, historialActivo.size()))
                    : historialActivo;
            String historialStr = objectMapper.writeValueAsString(historialParaGuardar);
            sesionRepository.findById(sesionActivaId).ifPresent(s -> {
                s.setHistorialJson(historialStr);
                sesionRepository.save(s);
            });
        } catch (Exception e) {
            System.err.println("Error persistiendo historial: " + e.getMessage());
        }
    }

    private void iniciarNuevaSesion() {
        // Buscar sesión activa sin cerrar del día
        List<Sesion> sesionesHoy = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(
                        LocalDateTime.now().withHour(0).withMinute(0));

        Sesion sesionSinCerrar = sesionesHoy.stream()
                .filter(s -> s.getFechaFin() == null &&
                        s.getHistorialJson() != null &&
                        !Boolean.TRUE.equals(s.getCierreVoluntario()))
                .findFirst()
                .orElse(null);

        if (sesionSinCerrar != null) {
            // Recuperar sesión existente
            sesionActivaId = sesionSinCerrar.getId();
            try {
                historialActivo = objectMapper.readValue(
                        sesionSinCerrar.getHistorialJson(),
                        new TypeReference<List<Map<String, Object>>>(){});
                System.out.println("Sesión recuperada: ID " + sesionActivaId +
                        " con " + historialActivo.size() + " mensajes");
            } catch (Exception e) {
                System.err.println("Error recuperando historial: " + e.getMessage());
                historialActivo = new ArrayList<>();
            }
        } else {
            Sesion sesion = new Sesion();
            sesion.setFechaInicio(LocalDateTime.now());
            sesion = sesionRepository.save(sesion);
            sesionActivaId = sesion.getId();
            historialActivo = new ArrayList<>();
            System.out.println("Nueva sesión iniciada: ID " + sesionActivaId);
        }
        inicioSesion = LocalDateTime.now();
    }

    public Map<String, Object> cerrarSesion() {
        Map<String, Object> resultado = new HashMap<>();
        if (sesionActivaId != null) {
            Long idParaCerrar = sesionActivaId;

            Optional<Sesion> sesionOpt = sesionRepository.findById(idParaCerrar);
            sesionOpt.ifPresent(sesion -> {
                sesion.setFechaFin(LocalDateTime.now());
                sesion.setHistorialJson(null);
                sesion.setCierreVoluntario(true);
                sesionRepository.save(sesion);

                // Si no hay reporte, pedirle al tutor que genere uno
                if (sesion.getReporte() == null && !historialActivo.isEmpty()) {
                    try {
                        Thread.sleep(2000);
                        List<Map<String, Object>> historialConCierre = new ArrayList<>(historialActivo);
                        historialConCierre.add(Map.of("role", "user", "content",
                                "La sesión terminó. Genera el REPORTE_SESION_START con lo que trabajamos hoy."));
                        String reporteAuto = claudeService.enviarConversacionCompleta(
                                obtenerSystemPrompt(), historialConCierre);
                        procesarBloqueReporteConId(reporteAuto, idParaCerrar);
                    } catch (Exception e) {
                        System.err.println("Error generando reporte automático: " + e.getMessage());
                    }
                }

                sesionRepository.findById(idParaCerrar).ifPresent(sesionFinal ->
                        System.out.println("Sesión cerrada: " + idParaCerrar +
                                " - reporte: " + (sesionFinal.getReporte() != null ? "OK" : "FALTA"))
                );
            });

            sesionActivaId = null;
            historialActivo = new ArrayList<>();
            inicioSesion = null;
        }
        resultado.put("status", "ok");
        return resultado;
    }

    private String obtenerSystemPrompt() {
        long leccionesHoy = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(
                        LocalDateTime.now().withHour(0).withMinute(0))
                .stream()
                .filter(s -> s.getReporte() != null && Boolean.TRUE.equals(s.getCierreVoluntario()))
                .count();

        long diasEstudiados = sesionRepository.findAllByOrderByFechaInicioDesc()
                .stream()
                .map(s -> s.getFechaInicio().toLocalDate())
                .distinct()
                .count();

        StringBuilder contextoHoy = new StringBuilder();
        contextoHoy.append("Lecciones completadas hoy: ").append(leccionesHoy).append(" de 5.\n");
        contextoHoy.append("Días estudiados en total: ").append(diasEstudiados).append(".\n");

        // Inyectar última lección completada para dar continuidad entre sesiones
        List<LeccionCompletada> lecciones = leccionRepository.findAllByOrderByFechaDescNumeroLeccionDesc();
        if (!lecciones.isEmpty()) {
            LeccionCompletada ultima = lecciones.get(0);
            contextoHoy.append("Última lección completada: ")
                    .append(ultima.getTema() != null ? ultima.getTema() : "sin tema")
                    .append(" — Nivel: ").append(ultima.getNivelComprension()).append("/10")
                    .append(" — Logro: ").append(ultima.getLogro() != null ? ultima.getLogro() : "-")
                    .append(" — Área a reforzar: ").append(ultima.getAreaReforzar() != null ? ultima.getAreaReforzar() : "-")
                    .append(".\n");
        }

        Optional<PerfilEstudiante> perfilOpt = perfilRepository.findFirstByOrderByIdAsc();
        if (perfilOpt.isPresent() && perfilOpt.get().getDiagnosticoCompletado()) {
            PerfilEstudiante perfil = perfilOpt.get();
            if (perfil.getSystemPromptPersonalizado() != null) {
                return contextoHoy + perfil.getSystemPromptPersonalizado();
            }
            return contextoHoy + systemPromptConfig.getPromptConPerfil(perfil.getPerfilCompleto());
        }
        return contextoHoy + systemPromptConfig.getPromptBase();
    }

    private void procesarBloquesDiagnostico(String respuesta) {
        if (respuesta.contains("PERFIL_ALEXIS_START") && respuesta.contains("PERFIL_ALEXIS_END")) {
            String perfil = extraerBloque(respuesta, "PERFIL_ALEXIS_START", "PERFIL_ALEXIS_END");
            PerfilEstudiante estudiante = perfilRepository.findFirstByOrderByIdAsc()
                    .orElse(new PerfilEstudiante());
            estudiante.setNombre("Alexis Leonardo");
            estudiante.setEdad(16);
            estudiante.setPerfilCompleto(perfil);
            estudiante.setDiagnosticoCompletado(true);
            estudiante.setFechaDiagnostico(LocalDateTime.now());
            estudiante.setFechaActualizacion(LocalDateTime.now());
            perfilRepository.save(estudiante);
        }

        if (respuesta.contains("PLAN_JSON_START") && respuesta.contains("PLAN_JSON_END")) {
            String json = extraerBloque(respuesta, "PLAN_JSON_START", "PLAN_JSON_END");
            parsearYGuardarPlan(json);
        }
    }

    private void parsearYGuardarPlan(String json) {
        try {
            objetivoRepository.deleteAll();
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode fases = root.get("fases");

            for (com.fasterxml.jackson.databind.JsonNode fase : fases) {
                String nombreFase = fase.get("fase").asText();
                for (com.fasterxml.jackson.databind.JsonNode obj : fase.get("objetivos")) {
                    ObjetivoEstudio objetivo = new ObjetivoEstudio();
                    objetivo.setFase(nombreFase);
                    objetivo.setNumeroSemana(obj.get("numeroSemana").asInt());
                    objetivo.setNombre(obj.get("nombre").asText());
                    objetivo.setSubtemas(obj.get("subtemas").asText());
                    objetivo.setProposito(obj.get("proposito").asText());
                    objetivo.setFechaInicio(LocalDate.parse(obj.get("fechaInicio").asText()));
                    objetivo.setFechaFin(LocalDate.parse(obj.get("fechaFin").asText()));
                    objetivo.setEstado("pendiente");
                    objetivoRepository.save(objetivo);
                }
            }
            System.out.println("Plan guardado: " + objetivoRepository.count() + " objetivos");
        } catch (Exception e) {
            System.err.println("Error parseando plan: " + e.getMessage());
        }
    }

    private void procesarBloqueReporte(String respuesta) {
        if (respuesta.contains("REPORTE_SESION_START") && sesionActivaId != null) {
            String reporte = extraerBloque(respuesta, "REPORTE_SESION_START", "REPORTE_SESION_END");
            sesionRepository.findById(sesionActivaId).ifPresent(sesion -> {
                sesion.setReporte(reporte);
                sesionRepository.save(sesion);
            });
            guardarLeccionCompletada(reporte);
        }
    }

    private void guardarLeccionCompletada(String reporte) {
        try {
            LeccionCompletada leccion = new LeccionCompletada();
            leccion.setFecha(LocalDate.now());

            for (String linea : reporte.split("\n")) {
                linea = linea.trim();
                if (linea.startsWith("Lección:")) {
                    try { leccion.setNumeroLeccion(Integer.parseInt(
                            linea.replace("Lección:", "").trim()));
                    } catch (Exception e) { leccion.setNumeroLeccion(0); }
                }
                if (linea.startsWith("Tema trabajado:"))
                    leccion.setTema(linea.replace("Tema trabajado:", "").trim());
                if (linea.startsWith("Nivel de comprensión:")) {
                    try {
                        String val = linea.replace("Nivel de comprensión:", "").trim();
                        leccion.setNivelComprension(Integer.parseInt(val.split("/")[0].trim()));
                    } catch (Exception e) { leccion.setNivelComprension(0); }
                }
                if (linea.startsWith("Logro del día:"))
                    leccion.setLogro(linea.replace("Logro del día:", "").trim());
                if (linea.startsWith("Área a reforzar:"))
                    leccion.setAreaReforzar(linea.replace("Área a reforzar:", "").trim());
            }

            if (leccion.getTema() == null || leccion.getTema().isEmpty()) {
                System.err.println("Lección sin tema — no se guarda");
                return;
            }
            if (leccion.getNumeroSemana() == null) leccion.setNumeroSemana(0);
            if (leccion.getNumeroLeccion() == null) leccion.setNumeroLeccion(0);

            // Determinar semana basado en fecha
            for (ObjetivoEstudio obj : objetivoRepository.findAllByOrderByNumeroSemanaAsc()) {
                if (!leccion.getFecha().isBefore(obj.getFechaInicio()) &&
                        !leccion.getFecha().isAfter(obj.getFechaFin())) {
                    leccion.setNumeroSemana(obj.getNumeroSemana());
                    break;
                }
            }

            leccionRepository.save(leccion);
            System.out.println("Lección guardada: semana " + leccion.getNumeroSemana() +
                    " lección " + leccion.getNumeroLeccion());
        } catch (Exception e) {
            System.err.println("Error guardando lección: " + e.getMessage());
        }
    }

    private void procesarBloqueExamen(String respuesta) {
        if (respuesta.contains("REPORTE_EXAMEN_START")) {
            String reporte = extraerBloque(respuesta, "REPORTE_EXAMEN_START", "REPORTE_EXAMEN_END");
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String calificacion = extraerCalificacion(reporte);
            emailService.enviarResultadoExamen(reporte, fecha, calificacion);

            // Guardar examen en BD
            try {
                ExamenResultado examen = new ExamenResultado();
                examen.setFecha(LocalDate.now());
                examen.setCalificacion(calificacion);

                for (String linea : reporte.split("\n")) {
                    linea = linea.trim();
                    if (linea.startsWith("Matemáticas:"))
                        examen.setMatematicas(linea.replace("Matemáticas:", "").trim());
                    if (linea.startsWith("Verbal:"))
                        examen.setVerbal(linea.replace("Verbal:", "").trim());
                    if (linea.startsWith("Errores clave:"))
                        examen.setErroresClave(linea.replace("Errores clave:", "").trim());
                    if (linea.startsWith("Recomendación:"))
                        examen.setRecomendacion(linea.replace("Recomendación:", "").trim());
                    if (linea.startsWith("Reprobado:"))
                        examen.setReprobado(linea.contains("sí") || linea.contains("si"));
                }

                examenRepository.save(examen);
                System.out.println("Examen guardado: " + calificacion);
            } catch (Exception e) {
                System.err.println("Error guardando examen: " + e.getMessage());
            }
        }
    }

    private String extraerCalificacion(String reporte) {
        for (String linea : reporte.split("\n")) {
            if (linea.startsWith("Calificación:"))
                return linea.replace("Calificación:", "").trim();
        }
        return "?/10";
    }

    private String extraerBloque(String texto, String inicio, String fin) {
        int idxInicio = texto.indexOf(inicio) + inicio.length();
        int idxFin = texto.indexOf(fin);
        if (idxInicio > 0 && idxFin > idxInicio)
            return texto.substring(idxInicio, idxFin).trim();
        return "";
    }

    private void guardarMensaje(Long sesionId, String rol, String contenido) {
        Mensaje msg = new Mensaje();
        msg.setRol(rol);
        msg.setContenido(contenido);
        msg.setTimestamp(LocalDateTime.now());
        Sesion sesion = new Sesion();
        sesion.setId(sesionId);
        msg.setSesion(sesion);
        mensajeRepository.save(msg);
    }

    private void procesarBloqueReporteConId(String respuesta, Long sesionId) {
        if (respuesta.contains("REPORTE_SESION_START")) {
            String reporte = extraerBloque(respuesta, "REPORTE_SESION_START", "REPORTE_SESION_END");
            sesionRepository.findById(sesionId).ifPresent(sesion -> {
                sesion.setReporte(reporte);
                sesionRepository.save(sesion);
            });
            guardarLeccionCompletada(reporte);
        }
    }

    public boolean isDiagnosticoCompletado() {
        return perfilRepository.findFirstByOrderByIdAsc()
                .map(PerfilEstudiante::getDiagnosticoCompletado)
                .orElse(false);
    }

    public long getTiempoSesionMinutos() {
        if (inicioSesion == null) return 0;
        return java.time.Duration.between(inicioSesion, LocalDateTime.now()).toMinutes();
    }

    public long getTotalSesiones() {
        return sesionRepository.count();
    }

    public Long getSesionActivaId() {
        return sesionActivaId;
    }
}