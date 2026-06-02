package com.tutor.alexis.controller;

import com.tutor.alexis.model.*;
import com.tutor.alexis.repository.*;
import com.tutor.alexis.service.ClaudeService;
import com.tutor.alexis.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ReporteController {

    @Autowired private SesionRepository sesionRepository;
    @Autowired private PerfilEstudianteRepository perfilRepository;
    @Autowired private ObjetivoEstudioRepository objetivoRepository;
    @Autowired private ClaudeService claudeService;
    @Autowired private EmailService emailService;
    @Autowired private LeccionCompletadaRepository leccionRepository;
    @Autowired private ExamenResultadoRepository examenRepository;
    @Autowired private MensajeRepository mensajeRepository;

    @GetMapping("/reporte")
    public String reporte(Model model) {
        List<Sesion> sesiones = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(LocalDateTime.now().minusDays(7));
        Optional<Sesion> sesionHoy = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(
                        LocalDateTime.now().withHour(0).withMinute(0))
                .stream().findFirst();
        Optional<PerfilEstudiante> perfil = perfilRepository.findFirstByOrderByIdAsc();

        long diasRestantes = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.of(2026, 8, 21));

        // Lecciones completadas hoy
        long leccionesHoy = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(
                        LocalDateTime.now().withHour(0).withMinute(0))
                .stream()
                .filter(s -> s.getReporte() != null)
                .count();

        // Racha de días consecutivos
        List<Sesion> todasSesiones = sesionRepository.findAllByOrderByFechaInicioDesc();
        long racha = calcularRacha(todasSesiones);

        // Última actividad
        String ultimaActividad = todasSesiones.isEmpty() ? "Sin actividad" :
                ChronoUnit.HOURS.between(
                        todasSesiones.get(0).getFechaInicio(), LocalDateTime.now()) + " horas";

        // Tema actual — último reporte
        String temaActual = sesiones.stream()
                .filter(s -> s.getReporte() != null)
                .findFirst()
                .map(s -> extraerTema(s.getReporte()))
                .orElse("Sin sesiones aún");

        // Nivel de comprensión promedio esta semana
        double promedioComprension = sesiones.stream()
                .filter(s -> s.getReporte() != null)
                .mapToInt(s -> extraerNivel(s.getReporte()))
                .filter(n -> n > 0)
                .average()
                .orElse(0);

        // Construir fases dinámicas
        List<Map<String, Object>> fases = new ArrayList<>();
        int objetivosAtrasados = 0;
        LocalDate hoy = LocalDate.now();

        List<ObjetivoEstudio> todosObjetivos = objetivoRepository.findAllByOrderByNumeroSemanaAsc();
        if (!todosObjetivos.isEmpty()) {
            for (ObjetivoEstudio obj : todosObjetivos) {
                if (!"completado".equals(obj.getEstado())) {
                    if (hoy.isAfter(obj.getFechaFin())) {
                        obj.setEstado("atrasado");
                    } else if (!hoy.isBefore(obj.getFechaInicio())) {
                        obj.setEstado("progreso");
                    }
                    objetivoRepository.save(obj);
                }
            }

            Map<String, List<ObjetivoEstudio>> porFase = new LinkedHashMap<>();
            for (ObjetivoEstudio obj : todosObjetivos) {
                porFase.computeIfAbsent(obj.getFase(), k -> new ArrayList<>()).add(obj);
            }

            for (Map.Entry<String, List<ObjetivoEstudio>> entry : porFase.entrySet()) {
                List<Map<String, Object>> objetivosUI = new ArrayList<>();
                int completados = 0;
                for (ObjetivoEstudio obj : entry.getValue()) {
                    if ("atrasado".equals(obj.getEstado())) objetivosAtrasados++;
                    if ("completado".equals(obj.getEstado())) completados++;
                    String icono = switch (obj.getEstado()) {
                        case "completado" -> "✅";
                        case "progreso"   -> "🔄";
                        case "atrasado"   -> "🔴";
                        default           -> "⏳";
                    };
                    String etiqueta = switch (obj.getEstado()) {
                        case "completado" -> "Completado";
                        case "progreso"   -> "En progreso";
                        case "atrasado"   -> "Atrasado";
                        default           -> "Pendiente";
                    };
                    Map<String, Object> objUI = new HashMap<>();
                    objUI.put("nombre", obj.getNombre());
                    objUI.put("subtemas", obj.getSubtemas());
                    objUI.put("proposito", obj.getProposito());
                    objUI.put("estado", obj.getEstado());
                    objUI.put("icono", icono);
                    objUI.put("etiqueta", etiqueta);
                    objUI.put("fechaTexto", "Semana del " + obj.getFechaInicio() + " al " + obj.getFechaFin());
                    objUI.put("numeroSemana", obj.getNumeroSemana());
                    objetivosUI.add(objUI);
                }
                int porcentaje = entry.getValue().isEmpty() ? 0 :
                        (completados * 100 / entry.getValue().size());
                Map<String, Object> fase = new HashMap<>();
                fase.put("nombre", entry.getKey());
                fase.put("objetivos", objetivosUI);
                fase.put("completados", completados);
                fase.put("total", entry.getValue().size());
                fase.put("porcentaje", porcentaje);
                fases.add(fase);
            }
        }

        model.addAttribute("sesiones", sesiones);
        model.addAttribute("sesionHoy", sesionHoy.orElse(null));
        model.addAttribute("perfil", perfil.orElse(null));
        model.addAttribute("totalSesiones", sesionRepository.count());
        model.addAttribute("diasRestantes", diasRestantes);
        model.addAttribute("fases", fases);
        model.addAttribute("objetivosAtrasados", objetivosAtrasados);
        model.addAttribute("leccionesHoy", leccionesHoy);
        model.addAttribute("racha", racha);
        model.addAttribute("ultimaActividad", ultimaActividad);
        model.addAttribute("temaActual", temaActual);
        model.addAttribute("promedioComprension", String.format("%.1f", promedioComprension));

        // Lecciones por semana para el dashboard detallado
        Map<Integer, List<LeccionCompletada>> leccionesPorSemana = new HashMap<>();
        List<LeccionCompletada> todasLecciones = leccionRepository.findAllByOrderByFechaDescNumeroLeccionDesc();
        for (LeccionCompletada lec : todasLecciones) {
            if (lec.getNumeroSemana() != null && lec.getNumeroSemana() > 0) {
                leccionesPorSemana.computeIfAbsent(lec.getNumeroSemana(), k -> new ArrayList<>()).add(lec);
            }
        }
        model.addAttribute("leccionesPorSemana", leccionesPorSemana);

        // Formatear sesiones con fechas legibles + índice de participación
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<Map<String, Object>> sesionesFormateadas = new ArrayList<>();
        List<Integer> scoresParticipacion = new ArrayList<>();

        for (Sesion s : sesiones) {
            Map<String, Object> sesionMap = new HashMap<>();
            sesionMap.put("fechaInicio", s.getFechaInicio().format(fmt));
            sesionMap.put("fechaFin", s.getFechaFin() != null ? s.getFechaFin().format(fmt) : "En curso");
            sesionMap.put("reporte", s.getReporte());

            // Calcular participación
            Map<String, Object> participacion = calcularParticipacion(s.getId());
            sesionMap.put("participacion", participacion);
            scoresParticipacion.add((Integer) participacion.get("score"));

            sesionesFormateadas.add(sesionMap);
        }
        model.addAttribute("sesionesFormateadas", sesionesFormateadas);

        // Participación promedio de la semana
        double promedioParticipacion = scoresParticipacion.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        model.addAttribute("promedioParticipacion", calcularNivelParticipacion((int) promedioParticipacion));
        model.addAttribute("promedioParticipacionScore", (int) promedioParticipacion);

        // Formatear sesión de hoy con participación
        if (sesionHoy.isPresent()) {
            Sesion s = sesionHoy.get();
            Map<String, Object> hoyMap = new HashMap<>();
            hoyMap.put("fechaInicio", s.getFechaInicio().format(fmt));
            hoyMap.put("fechaFin", s.getFechaFin() != null ? s.getFechaFin().format(fmt) : "En curso");
            hoyMap.put("reporte", s.getReporte());
            hoyMap.put("participacion", calcularParticipacion(s.getId()));
            model.addAttribute("sesionHoyFormateada", hoyMap);
        } else {
            model.addAttribute("sesionHoyFormateada", null);
        }

        List<ExamenResultado> examenes = examenRepository.findAllByOrderByFechaDesc();
        model.addAttribute("examenes", examenes);

        return "reporte";
    }

    /**
     * Calcula el índice de participación de Alexis en una sesión.
     * Score 0-100 basado en:
     * - Cantidad de mensajes del usuario (40%)
     * - Longitud promedio de sus mensajes (40%)
     * - Gaps de tiempo entre mensajes (20% penalización)
     */
    private Map<String, Object> calcularParticipacion(Long sesionId) {
        Map<String, Object> resultado = new HashMap<>();
        List<Mensaje> mensajes = mensajeRepository.findBySesionIdOrderByTimestamp(sesionId);

        List<Mensaje> mensajesAlexis = mensajes.stream()
                .filter(m -> "user".equals(m.getRol()))
                .collect(Collectors.toList());

        if (mensajesAlexis.isEmpty()) {
            resultado.put("score", 0);
            resultado.put("nivel", "Sin datos");
            resultado.put("clase", "gris");
            resultado.put("mensajes", 0);
            resultado.put("longitudPromedio", 0);
            resultado.put("gapPromedio", 0);
            return resultado;
        }

        // Factor 1: cantidad de mensajes (ideal: 15-25 en 40 min)
        int cantidadMensajes = mensajesAlexis.size();
        int scoreMensajes = Math.min(100, (cantidadMensajes * 100) / 20); // 20 mensajes = 100%

        // Factor 2: longitud promedio de mensajes (ideal: >50 chars = razona, explica)
        double longitudPromedio = mensajesAlexis.stream()
                .mapToInt(m -> m.getContenido() != null ? m.getContenido().length() : 0)
                .average()
                .orElse(0);
        int scoreLongitud = (int) Math.min(100, (longitudPromedio * 100) / 80); // 80 chars = 100%

        // Factor 3: gaps entre mensajes (penaliza gaps >5 min)
        long gapPromedio = 0;
        int penalizacionGaps = 0;
        if (mensajes.size() > 1) {
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < mensajes.size(); i++) {
                long gap = ChronoUnit.MINUTES.between(
                        mensajes.get(i-1).getTimestamp(),
                        mensajes.get(i).getTimestamp());
                if (gap > 0 && gap < 30) gaps.add(gap); // ignora gaps >30 min (descansos)
            }
            if (!gaps.isEmpty()) {
                gapPromedio = gaps.stream().mapToLong(Long::longValue).sum() / gaps.size();
                // Penalización: gap promedio >5 min reduce score
                penalizacionGaps = (int) Math.min(30, Math.max(0, (gapPromedio - 5) * 5));
            }
        }

        // Score final ponderado
        int scoreFinal = (int) ((scoreMensajes * 0.4) + (scoreLongitud * 0.4)) - penalizacionGaps;
        scoreFinal = Math.max(0, Math.min(100, scoreFinal));

        resultado.put("score", scoreFinal);
        resultado.put("nivel", calcularNivelParticipacion(scoreFinal));
        resultado.put("clase", scoreFinal >= 70 ? "verde" : scoreFinal >= 40 ? "amarillo" : "rojo");
        resultado.put("mensajes", cantidadMensajes);
        resultado.put("longitudPromedio", (int) longitudPromedio);
        resultado.put("gapPromedio", gapPromedio);

        return resultado;
    }

    private String calcularNivelParticipacion(int score) {
        if (score >= 70) return "Alto";
        if (score >= 40) return "Medio";
        if (score > 0)   return "Bajo";
        return "Sin datos";
    }

    private long calcularRacha(List<Sesion> sesiones) {
        if (sesiones.isEmpty()) return 0;
        Set<LocalDate> diasConSesion = sesiones.stream()
                .map(s -> s.getFechaInicio().toLocalDate())
                .collect(Collectors.toSet());
        long racha = 0;
        LocalDate dia = LocalDate.now();
        while (diasConSesion.contains(dia)) {
            racha++;
            dia = dia.minusDays(1);
        }
        return racha;
    }

    private String extraerTema(String reporte) {
        if (reporte == null) return "Sin datos";
        for (String linea : reporte.split("\n")) {
            if (linea.startsWith("Tema trabajado:")) {
                return linea.replace("Tema trabajado:", "").trim();
            }
        }
        return "Sin datos";
    }

    private int extraerNivel(String reporte) {
        if (reporte == null) return 0;
        for (String linea : reporte.split("\n")) {
            if (linea.startsWith("Nivel de comprensión:")) {
                try {
                    String valor = linea.replace("Nivel de comprensión:", "").trim();
                    return Integer.parseInt(valor.split("/")[0].trim());
                } catch (Exception e) { return 0; }
            }
        }
        return 0;
    }

    @GetMapping("/enviar-resumen-semanal")
    @ResponseBody
    public String enviarResumenSemanal() {
        List<Sesion> sesiones = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(LocalDateTime.now().minusDays(7));
        if (sesiones.isEmpty()) return "No hay sesiones esta semana";
        StringBuilder reportes = new StringBuilder();
        for (Sesion s : sesiones) {
            if (s.getReporte() != null) reportes.append(s.getReporte()).append("\n---\n");
        }
        if (reportes.length() == 0) return "No hay reportes esta semana";
        String prompt = "Eres el tutor de Alexis. Basándote en estos reportes de la semana, " +
                "genera un resumen ejecutivo para su mamá con:\n" +
                "- Qué aprendió esta semana\n- Cómo fue su actitud general\n" +
                "- Sus logros más importantes\n- Qué necesita reforzar la próxima semana\n" +
                "- Calificación general del 1 al 10\n- Recomendación para los papás\n\n" +
                "Reportes:\n" + reportes;
        String resumen = claudeService.enviarConversacionCompleta(
                "Eres el tutor personal de Alexis Leonardo.",
                List.of(Map.of("role", "user", "content", prompt)));
        String semana = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "MX")));
        emailService.enviarResumenSemanal(resumen, semana);
        return "Resumen enviado ✅";
    }

    @GetMapping("/init-objetivos-prueba")
    @ResponseBody
    public String initObjetivosPrueba() {
        return "Ya no necesario — el plan se genera dinámicamente desde el diagnóstico.";
    }
}