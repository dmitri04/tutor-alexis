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
    @Autowired private InternetControlService internetControlService;

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

        // Detectar errores de conexion
        if (respuesta.contains("Sin conexion") || respuesta.contains("Failed to resolve")) {
            erroresConsecutivos++;
            System.err.println("ERROR CONEXION #" + erroresConsecutivos + " - Alexis no puede estudiar");
        } else {
            erroresConsecutivos = 0;
        }

        guardarMensaje(sesionActivaId, "assistant", respuesta);
        historialActivo.add(Map.of("role", "assistant", "content", respuesta));

        // Persistir historial en BD despues de cada mensaje
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
        // Barrida de sesiones fantasma antes de empezar (Bug 1):
        // limpia arranques vacios de dias anteriores para que no se acumulen.
        limpiarSesionesFantasma();

        // Buscar sesion activa sin cerrar del dia
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
            // Recuperar sesion existente
            sesionActivaId = sesionSinCerrar.getId();
            try {
                historialActivo = objectMapper.readValue(
                        sesionSinCerrar.getHistorialJson(),
                        new TypeReference<List<Map<String, Object>>>(){});
                System.out.println("Sesion recuperada: ID " + sesionActivaId +
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
            System.out.println("Nueva sesion iniciada: ID " + sesionActivaId);
        }
        inicioSesion = LocalDateTime.now();

        // Modo estudio: bloquear internet en la laptop de Alexis (asincrono)
        internetControlService.bloquearAsync();
    }

    /**
     * Borra sesiones fantasma: arranques vacios que quedan cuando alguien abre
     * el chat y se va sin trabajar, o cuando se reinicia la app. Una sesion es
     * fantasma si: no tiene reporte, no tiene historial activo, y tiene 2 o menos
     * mensajes (un "hola" y una respuesta, sin trabajo real).
     *
     * SEGURIDAD: nunca borra
     *   - la sesion activa actual (sesionActivaId)
     *   - sesiones de HOY (podrian estar en uso)
     *   - sesiones con reporte (trabajo registrado)
     *   - sesiones con 3+ mensajes (trabajo real aunque no haya reporte)
     * Asi jamas se pierde una sesion con contenido de Alexis.
     */
    private void limpiarSesionesFantasma() {
        try {
            LocalDate hoy = LocalDate.now();
            List<Sesion> todas = sesionRepository.findAllByOrderByFechaInicioDesc();
            int borradas = 0;
            for (Sesion s : todas) {
                // No tocar la sesion activa
                if (sesionActivaId != null && sesionActivaId.equals(s.getId())) continue;
                // No tocar sesiones de hoy
                if (s.getFechaInicio() != null && s.getFechaInicio().toLocalDate().equals(hoy)) continue;
                // No tocar sesiones con reporte (trabajo registrado)
                if (s.getReporte() != null) continue;
                // No tocar sesiones con historial activo (sin cerrar bien)
                if (s.getHistorialJson() != null && !s.getHistorialJson().isEmpty()
                        && !s.getHistorialJson().equals("[]")) continue;
                // Contar mensajes: 3 o mas = trabajo real, no se borra
                long numMensajes = mensajeRepository.findBySesionIdOrderByTimestamp(s.getId()).size();
                if (numMensajes >= 3) continue;

                // Es fantasma: borrar sus mensajes (si hay) y la sesion
                mensajeRepository.findBySesionIdOrderByTimestamp(s.getId())
                        .forEach(mensajeRepository::delete);
                sesionRepository.delete(s);
                borradas++;
            }
            if (borradas > 0) {
                System.out.println("Barrida: " + borradas + " sesion(es) fantasma eliminada(s)");
            }
        } catch (Exception e) {
            // Si la barrida falla, no debe romper el inicio de sesion
            System.err.println("Error en barrida de fantasmas (ignorado): " + e.getMessage());
        }
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
                                "La sesion termino. Genera el REPORTE_SESION_START con lo que trabajamos hoy."));
                        String reporteAuto = claudeService.enviarConversacionCompleta(
                                obtenerSystemPrompt(), historialConCierre);
                        procesarBloqueReporteConId(reporteAuto, idParaCerrar);
                    } catch (Exception e) {
                        System.err.println("Error generando reporte automatico: " + e.getMessage());
                    }
                }

                sesionRepository.findById(idParaCerrar).ifPresent(sesionFinal -> {
                    // Duracion (reloj del servidor, confiable)
                    String duracion = "?";
                    if (sesionFinal.getFechaInicio() != null && sesionFinal.getFechaFin() != null) {
                        long mins = java.time.Duration.between(
                                sesionFinal.getFechaInicio(), sesionFinal.getFechaFin()).toMinutes();
                        duracion = mins + " min";
                    }
                    // Calificacion: la saca del reporte. Funciona para sesion normal
                    // ("Nivel de comprensión: X/10") y para examen ("Calificación: X/10").
                    String calificacion = "s/n";
                    String tipo = "sesion";
                    String rep = sesionFinal.getReporte();
                    if (rep != null) {
                        boolean esExamen = rep.contains("REPORTE_EXAMEN") || rep.contains("Calificación:");
                        String etiqueta = esExamen ? "Calificación:" : "Nivel de comprensión:";
                        if (esExamen) tipo = "EXAMEN";
                        for (String linea : rep.split("\n")) {
                            if (linea.trim().startsWith(etiqueta)) {
                                calificacion = linea.replace(etiqueta, "").trim();
                                break;
                            }
                        }
                    }
                    System.out.println("Sesion cerrada: " + idParaCerrar +
                            " - reporte: " + (rep != null ? "OK" : "FALTA") +
                            " - tipo: " + tipo +
                            " - duracion: " + duracion +
                            " - nivel: " + calificacion);
                });
            });

            sesionActivaId = null;
            historialActivo = new ArrayList<>();
            inicioSesion = null;

            // Detener el re-bloqueo periodico (internet sigue bloqueado hasta desbloqueo manual)
            internetControlService.detenerRebloqueoSesion();
        }
        resultado.put("status", "ok");
        return resultado;
    }

    /**
     * Determina si toca evaluacion: cada 6 sesiones validas (nivel >= 7)
     * desde el ultimo examen.
     *
     * BLINDAJE CONTRA LOOP DE EXAMENES:
     * El "ultimo examen" se calcula como el MAXIMO entre dos fuentes:
     *   (a) la fecha mas reciente en la tabla examen_resultado, y
     *   (b) la fecha de la sesion mas reciente cuyo reporte contiene REPORTE_EXAMEN.
     *
     * Por que: si el guardado en examen_resultado falla (p. ej. choque de PK),
     * la tabla queda desactualizada y el contador nunca avanza, disparando
     * examen en cada sesion (loop). Pero la SESION que hizo el examen SI guarda
     * su reporte con REPORTE_EXAMEN. Mirando ambas fuentes, un fallo de guardado
     * ya no causa loop: la sesion delata que el examen ocurrio.
     */
    private boolean tocaExamen() {
        // Si ya hay una sesion activa que es examen, no volver a disparar
        if (sesionActivaId != null) {
            Optional<Sesion> actual = sesionRepository.findById(sesionActivaId);
            if (actual.isPresent() && actual.get().getReporte() != null
                    && actual.get().getReporte().contains("REPORTE_EXAMEN")) {
                return false;
            }
        }

        // (a) Fecha del ultimo examen en la tabla de resultados
        LocalDate fechaExamenTabla = examenRepository.findAll().stream()
                .map(ExamenResultado::getFecha)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        // (b) Fecha de la sesion mas reciente que contiene un REPORTE_EXAMEN
        //     (esto cubre el caso de que el guardado en examen_resultado haya fallado)
        LocalDate fechaExamenSesion = sesionRepository.findAllByOrderByFechaInicioDesc().stream()
                .filter(s -> s.getReporte() != null && s.getReporte().contains("REPORTE_EXAMEN"))
                .map(s -> s.getFechaInicio().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(null);

        // El ultimo examen real es el mas reciente de las dos fuentes
        LocalDate ultimoExamen = null;
        if (fechaExamenTabla != null && fechaExamenSesion != null) {
            ultimoExamen = fechaExamenTabla.isAfter(fechaExamenSesion) ? fechaExamenTabla : fechaExamenSesion;
        } else if (fechaExamenTabla != null) {
            ultimoExamen = fechaExamenTabla;
        } else if (fechaExamenSesion != null) {
            ultimoExamen = fechaExamenSesion;
        }

        // Contar lecciones validas (nivel >= 7) ESTRICTAMENTE posteriores al ultimo examen.
        // isAfter (no isBefore-negado): las lecciones del mismo dia del examen no cuentan.
        final LocalDate corte = ultimoExamen;
        long sesionesValidasDesdeExamen = leccionRepository.findAllByOrderByFechaDescNumeroLeccionDesc()
                .stream()
                .filter(l -> l.getNivelComprension() != null && l.getNivelComprension() >= 7)
                .filter(l -> {
                    if (corte == null) return true; // nunca ha habido examen
                    return l.getFecha() != null && l.getFecha().isAfter(corte);
                })
                .count();

        return sesionesValidasDesdeExamen >= 6;
    }

    private String obtenerSystemPrompt() {
        // Cuenta solo lecciones aprobadas (nivel >= 7) las de 6 o menos se repiten
        long leccionesHoy = leccionRepository.findAllByOrderByFechaDescNumeroLeccionDesc()
                .stream()
                .filter(l -> l.getFecha() != null && l.getFecha().equals(LocalDate.now()))
                .filter(l -> l.getNivelComprension() != null && l.getNivelComprension() >= 7)
                .count();

        long diasEstudiados = sesionRepository.findAllByOrderByFechaInicioDesc()
                .stream()
                .map(s -> s.getFechaInicio().toLocalDate())
                .distinct()
                .count();

        StringBuilder contextoHoy = new StringBuilder();

        // ===== SEMANA ACTUAL DEL PLAN =====
        // Busca el objetivo_estudio cuyo rango de fechas incluye hoy
        // e inyecta sus temas en el contexto para que el tutor los siga.
        LocalDate hoy = LocalDate.now();
        objetivoRepository.findAllByOrderByNumeroSemanaAsc().stream()
                .filter(o -> !hoy.isBefore(o.getFechaInicio()) && !hoy.isAfter(o.getFechaFin()))
                .findFirst()
                .ifPresentOrElse(semanaActual -> {
                    contextoHoy.append("=== PLAN DE ESTA SEMANA ===\n");
                    contextoHoy.append("Semana ").append(semanaActual.getNumeroSemana())
                            .append(" - ").append(semanaActual.getNombre()).append("\n");
                    contextoHoy.append("Fase: ").append(semanaActual.getFase()).append("\n");
                    contextoHoy.append("Subtemas a trabajar esta semana:\n")
                            .append(semanaActual.getSubtemas()).append("\n");
                    contextoHoy.append("Proposito pedagogico: ")
                            .append(semanaActual.getProposito()).append("\n");
                    contextoHoy.append("=========================\n\n");
                }, () -> contextoHoy.append(
                        "AVISO: hoy esta fuera del rango del plan (revisa fechas en objetivo_estudio).\n\n"));

        // ===== DETECCION DE EXAMEN AUTOMATICO =====
        // Cada 6 sesiones validas (nivel >= 7) desde el ultimo examen, toca evaluacion.
        // El examen es una sesion que cuenta pero NO se repite (genera REPORTE_EXAMEN).
        if (tocaExamen()) {
            contextoHoy.append("=== INSTRUCCION PRIORITARIA: HOY TOCA EVALUACION ===\n");
            contextoHoy.append("Alexis ya completo 6 sesiones desde su ultima evaluacion. ");
            contextoHoy.append("Esta sesion es una EVALUACION, no una leccion normal. ");
            contextoHoy.append("Saludalo con naturalidad, dile que es momento de su evaluacion periodica ");
            contextoHoy.append("(para ver como va su pensamiento, sin presion), y arranca el modo evaluacion: ");
            contextoHoy.append("una pregunta corta a la vez, pidiendo el porque despues de cada respuesta, ");
            contextoHoy.append("cubriendo razonamiento verbal y matematico. Evalua el PROCESO de pensamiento. ");
            contextoHoy.append("Al terminar genera el bloque REPORTE_EXAMEN_START/END. ");
            contextoHoy.append("Esta instruccion tiene prioridad sobre cualquier regla de inicio normal.\n\n");
        }

        contextoHoy.append("Sesiones validas completadas hoy: ").append(leccionesHoy).append(" de 6 (maximo del dia).\n");
        contextoHoy.append("Dias estudiados en total: ").append(diasEstudiados).append(".\n");

        // Inyectar ultima leccion completada para dar continuidad entre sesiones
        List<LeccionCompletada> lecciones = leccionRepository.findAllByOrderByFechaDescNumeroLeccionDesc();
        if (!lecciones.isEmpty()) {
            LeccionCompletada ultima = lecciones.get(0);
            contextoHoy.append("Ultima leccion completada: ")
                    .append(ultima.getTema() != null ? ultima.getTema() : "sin tema")
                    .append(" - Nivel: ").append(ultima.getNivelComprension()).append("/10")
                    .append(" - Logro: ").append(ultima.getLogro() != null ? ultima.getLogro() : "-")
                    .append(" - Area a reforzar: ").append(ultima.getAreaReforzar() != null ? ultima.getAreaReforzar() : "-")
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
                    try {
                        // Extraer el primer número de la línea, ignorando texto extra
                        // como "2 de 6" → 2, o "Lección 3" → 3. Antes "2 de 6" tronaba
                        // el parseInt y caía en 0.
                        String resto = linea.replace("Lección:", "").trim();
                        java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("\\d+").matcher(resto);
                        if (m.find()) {
                            leccion.setNumeroLeccion(Integer.parseInt(m.group()));
                        } else {
                            leccion.setNumeroLeccion(0);
                        }
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
                System.err.println("Leccion sin tema no se guarda");
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
            System.out.println("Leccion guardada: semana " + leccion.getNumeroSemana() +
                    " leccion " + leccion.getNumeroLeccion());
        } catch (Exception e) {
            System.err.println("Error guardando leccion: " + e.getMessage());
        }
    }

    /**
     * Procesa el bloque REPORTE_EXAMEN. Guardado IDEMPOTENTE: un examen por dia.
     * Si ya existe un examen con la fecha de hoy, lo ACTUALIZA en vez de crear
     * uno nuevo (evita el choque de PK que antes dejaba el contador en loop).
     */
    private void procesarBloqueExamen(String respuesta) {
        if (respuesta.contains("REPORTE_EXAMEN_START")) {
            String reporte = extraerBloque(respuesta, "REPORTE_EXAMEN_START", "REPORTE_EXAMEN_END");
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String calificacion = extraerCalificacion(reporte);
            emailService.enviarResultadoExamen(reporte, fecha, calificacion);

            try {
                // Idempotente: reusa el examen de hoy si ya existe, si no crea uno nuevo.
                ExamenResultado examen = examenRepository.findFirstByFecha(LocalDate.now())
                        .orElseGet(ExamenResultado::new);
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
                        examen.setReprobado(linea.contains("si"));
                }

                examenRepository.save(examen);
                System.out.println("Examen guardado/actualizado: " + calificacion + " (fecha " + LocalDate.now() + ")");
            } catch (Exception e) {
                // Aunque el guardado falle, tocaExamen() ya no entra en loop:
                // la sesion guardo su reporte con REPORTE_EXAMEN y eso cuenta como
                // "examen realizado" para el calculo del ultimo examen.
                System.err.println("Error guardando examen (no causa loop, la sesion lo registra): " + e.getMessage());
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