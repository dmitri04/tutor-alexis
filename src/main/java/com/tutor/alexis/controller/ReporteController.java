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
import java.util.TreeMap;
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
        int scorePromedio = (int) promedioParticipacion;
        String nivelPromedio = scorePromedio >= 100 ? "Alto" : scorePromedio >= 50 ? "Medio" : "Bajo";
        model.addAttribute("promedioParticipacion", nivelPromedio);
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

        // Enriquecer exámenes con porcentajes para barras visuales
        // Gráfica de comprensión semanal
        List<Map<String, Object>> comprensionSemanal = new ArrayList<>();
        List<LeccionCompletada> todasParaGrafica = leccionRepository.findAllByOrderByFechaDescNumeroLeccionDesc();
        Map<Integer, List<Integer>> nivelesPorSemana = new LinkedHashMap<>();
        for (LeccionCompletada lec : todasParaGrafica) {
            if (lec.getNumeroSemana() != null && lec.getNivelComprension() != null && lec.getNivelComprension() > 0) {
                nivelesPorSemana.computeIfAbsent(lec.getNumeroSemana(), k -> new ArrayList<>()).add(lec.getNivelComprension());
            }
        }
        // Ordenar por semana ascendente
        new TreeMap<>(nivelesPorSemana).forEach((semana, niveles) -> {
            double promedio = niveles.stream().mapToInt(Integer::intValue).average().orElse(0);
            Map<String, Object> punto = new HashMap<>();
            punto.put("semana", semana);
            punto.put("promedio", Math.round(promedio * 10.0) / 10.0);
            punto.put("lecciones", niveles.size());
            comprensionSemanal.add(punto);
        });
        // Normalizar alturas de barras: min=20%, max=100%, escalado al rango real
        if (!comprensionSemanal.isEmpty()) {
            double minVal = comprensionSemanal.stream()
                    .mapToDouble(p -> (Double) p.get("promedio")).min().orElse(0);
            double maxVal = comprensionSemanal.stream()
                    .mapToDouble(p -> (Double) p.get("promedio")).max().orElse(10);
            double rango = maxVal - minVal;
            for (Map<String, Object> punto : comprensionSemanal) {
                double val = (Double) punto.get("promedio");
                int altura = rango < 0.1
                        ? 60  // todas iguales, altura media
                        : (int) (20 + ((val - minVal) / rango) * 75);
                punto.put("altura", altura);
            }
        }
        model.addAttribute("comprensionSemanal", comprensionSemanal);

        List<ExamenResultado> examenesRaw = examenRepository.findAllByOrderByFechaDesc();
        List<Map<String, Object>> examenes = new ArrayList<>();
        for (ExamenResultado ex : examenesRaw) {
            Map<String, Object> exMap = new HashMap<>();
            exMap.put("fecha", ex.getFecha());
            exMap.put("calificacion", ex.getCalificacion());
            exMap.put("matematicas", ex.getMatematicas());
            exMap.put("verbal", ex.getVerbal());
            exMap.put("erroresClave", ex.getErroresClave());
            exMap.put("recomendacion", ex.getRecomendacion());
            exMap.put("reprobado", ex.getReprobado());
            // Extraer X de "X/5 — observación" para calcular porcentaje de barra
            exMap.put("pctMat", extraerPorcentajeBarra(ex.getMatematicas()));
            exMap.put("pctVer", extraerPorcentajeBarra(ex.getVerbal()));
            examenes.add(exMap);
        }
        model.addAttribute("examenes", examenes);

        return "reporte";
    }

    /**
     * Calcula participación objetiva de Alexis en una sesión.
     * Muestra datos directos: respuestas enviadas + tiempo de respuesta promedio.
     */
    private Map<String, Object> calcularParticipacion(Long sesionId) {
        Map<String, Object> resultado = new HashMap<>();
        List<Mensaje> mensajes = mensajeRepository.findBySesionIdOrderByTimestamp(sesionId);

        List<Mensaje> mensajesAlexis = mensajes.stream()
                .filter(m -> "user".equals(m.getRol()))
                .collect(Collectors.toList());

        if (mensajesAlexis.isEmpty()) {
            resultado.put("score", 0);
            resultado.put("respuestas", 0);
            resultado.put("tiempoRespuesta", 0);
            resultado.put("clase", "gris");
            resultado.put("icono", "❓");
            return resultado;
        }

        int respuestas = mensajesAlexis.size();

        // Tiempo promedio de respuesta en segundos: gap entre mensaje tutor → respuesta Alexis
        List<Long> tiemposRespuesta = new ArrayList<>();
        for (int i = 1; i < mensajes.size(); i++) {
            Mensaje anterior = mensajes.get(i - 1);
            Mensaje actual   = mensajes.get(i);
            if ("assistant".equals(anterior.getRol()) && "user".equals(actual.getRol())) {
                long gapSeg = ChronoUnit.SECONDS.between(
                        anterior.getTimestamp(), actual.getTimestamp());
                if (gapSeg >= 0 && gapSeg < 1200) // ignora gaps >20 min (descansos reales)
                    tiemposRespuesta.add(gapSeg);
            }
        }

        long tiempoPromedioSeg = tiemposRespuesta.isEmpty() ? 0 :
                tiemposRespuesta.stream().mapToLong(Long::longValue).sum() / tiemposRespuesta.size();

        // Formato legible: "<1 min", "1 min", "2 min", etc.
        String tiempoTexto = tiempoPromedioSeg < 60
                ? tiempoPromedioSeg + " seg"
                : (tiempoPromedioSeg / 60) + " min";

        // Clasificación objetiva (usando segundos)
        boolean trabajoBien   = respuestas >= 8 && tiempoPromedioSeg <= 300; // ≤5 min
        boolean trabajoDudoso = respuestas >= 5 || tiempoPromedioSeg <= 480; // ≤8 min

        String clase, icono;
        int score;
        if (trabajoBien) {
            clase = "verde";   icono = "✅"; score = 100;
        } else if (trabajoDudoso) {
            clase = "amarillo"; icono = "⚠️"; score = 50;
        } else {
            clase = "rojo";    icono = "❌"; score = 10;
        }

        resultado.put("score", score);
        resultado.put("respuestas", respuestas);
        resultado.put("tiempoRespuesta", tiempoTexto);
        resultado.put("clase", clase);
        resultado.put("icono", icono);

        return resultado;
    }

    /**
     * Extrae el número de "X/5 — observación" y devuelve porcentaje 0-100
     */
    private int extraerPorcentajeBarra(String valor) {
        if (valor == null || valor.isEmpty()) return 0;
        try {
            String parte = valor.split("/")[0].trim();
            int num = Integer.parseInt(parte);
            return Math.min(100, num * 20); // X/5 → porcentaje sobre 100
        } catch (Exception e) { return 0; }
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