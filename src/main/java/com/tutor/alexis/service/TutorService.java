package com.tutor.alexis.service;

import com.tutor.alexis.config.SystemPromptConfig;
import com.tutor.alexis.model.Mensaje;
import com.tutor.alexis.model.ObjetivoEstudio;
import com.tutor.alexis.model.PerfilEstudiante;
import com.tutor.alexis.model.Sesion;
import com.tutor.alexis.repository.MensajeRepository;
import com.tutor.alexis.repository.ObjetivoEstudioRepository;
import com.tutor.alexis.repository.PerfilEstudianteRepository;
import com.tutor.alexis.repository.SesionRepository;
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

    private Long sesionActivaId = null;
    private List<Map<String, Object>> historialActivo = new ArrayList<>();
    private LocalDateTime inicioSesion = null;

    public String procesarMensaje(String mensajeUsuario) {
        return procesarMensajeConImagen(mensajeUsuario, null, null);
    }

    public String procesarMensajeConImagen(String mensajeUsuario, String imagenBase64, String mediaType) {
        if (sesionActivaId == null) {
            iniciarNuevaSesion();
        }

        // Construir contenido del mensaje
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
        String respuesta = claudeService.enviarConversacionCompleta(systemPrompt, historialActivo);

        guardarMensaje(sesionActivaId, "assistant", respuesta);
        historialActivo.add(Map.of("role", "assistant", "content", respuesta));

        procesarBloquesDiagnostico(respuesta);
        procesarBloqueReporte(respuesta);
        procesarBloqueExamen(respuesta);

        return respuesta;
    }

    private void iniciarNuevaSesion() {
        Sesion sesion = new Sesion();
        sesion.setFechaInicio(LocalDateTime.now());
        sesion = sesionRepository.save(sesion);
        sesionActivaId = sesion.getId();
        inicioSesion = LocalDateTime.now();
        historialActivo = new ArrayList<>();
    }

    public Map<String, Object> cerrarSesion() {
        Map<String, Object> resultado = new HashMap<>();
        if (sesionActivaId != null) {
            Optional<Sesion> sesionOpt = sesionRepository.findById(sesionActivaId);
            sesionOpt.ifPresent(sesion -> {
                sesion.setFechaFin(LocalDateTime.now());
                sesionRepository.save(sesion);

                // Si no hay reporte, pedirle al tutor que genere uno
                if (sesion.getReporte() == null && !historialActivo.isEmpty()) {
                    String reporteAuto = claudeService.enviarConversacionCompleta(
                            obtenerSystemPrompt(),
                            new ArrayList<>(historialActivo) {{
                                add(Map.of("role", "user", "content",
                                        "La sesión de estudio terminó. Genera el REPORTE_SESION_START con lo que trabajamos hoy."));
                            }}
                    );
                    procesarBloqueReporte(reporteAuto);
                }

                // Recargar sesión actualizada
                Sesion sesionFinal = sesionRepository.findById(sesionActivaId).orElse(sesion);

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
                .filter(s -> s.getReporte() != null)
                .count();

        long diasEstudiados = sesionRepository.findAllByOrderByFechaInicioDesc()
                .stream()
                .map(s -> s.getFechaInicio().toLocalDate())
                .distinct()
                .count();

        String contextoHoy = "Lecciones completadas hoy: " + leccionesHoy + " de 4.\n" +
                "Días estudiados en total: " + diasEstudiados + ".\n";

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
            objetivoRepository.deleteAll(); // limpiar plan anterior

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode fases = root.get("fases");

            for (com.fasterxml.jackson.databind.JsonNode fase : fases) {
                String nombreFase = fase.get("fase").asText();
                com.fasterxml.jackson.databind.JsonNode objetivos = fase.get("objetivos");

                for (com.fasterxml.jackson.databind.JsonNode obj : objetivos) {
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
            Optional<Sesion> sesionOpt = sesionRepository.findById(sesionActivaId);
            sesionOpt.ifPresent(sesion -> {
                sesion.setReporte(reporte);
                sesionRepository.save(sesion);
            });
        }
    }

    private void procesarBloqueExamen(String respuesta) {
        if (respuesta.contains("REPORTE_EXAMEN_START")) {
            String reporte = extraerBloque(respuesta, "REPORTE_EXAMEN_START", "REPORTE_EXAMEN_END");
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String calificacion = extraerCalificacion(reporte);
            emailService.enviarResultadoExamen(reporte, fecha, calificacion);
        }
    }

    private String extraerCalificacion(String reporte) {
        for (String linea : reporte.split("\n")) {
            if (linea.startsWith("Calificación:")) {
                return linea.replace("Calificación:", "").trim();
            }
        }
        return "?/10";
    }

    private String extraerBloque(String texto, String inicio, String fin) {
        int idxInicio = texto.indexOf(inicio) + inicio.length();
        int idxFin = texto.indexOf(fin);
        if (idxInicio > 0 && idxFin > idxInicio) {
            return texto.substring(idxInicio, idxFin).trim();
        }
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
