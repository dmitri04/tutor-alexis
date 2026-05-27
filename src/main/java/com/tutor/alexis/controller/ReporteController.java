package com.tutor.alexis.controller;

import com.tutor.alexis.model.ObjetivoEstudio;
import com.tutor.alexis.model.PerfilEstudiante;
import com.tutor.alexis.model.Sesion;
import com.tutor.alexis.repository.ObjetivoEstudioRepository;
import com.tutor.alexis.repository.PerfilEstudianteRepository;
import com.tutor.alexis.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller
public class ReporteController {

    @Autowired private SesionRepository sesionRepository;
    @Autowired private PerfilEstudianteRepository perfilRepository;
    @Autowired private ObjetivoEstudioRepository objetivoRepository;

    @GetMapping("/reporte")
    public String reporte(Model model) {
        List<Sesion> sesiones = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(LocalDateTime.now().minusDays(7));
        Optional<Sesion> sesionHoy = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(
                        LocalDateTime.now().withHour(0).withMinute(0))
                .stream().findFirst();
        Optional<PerfilEstudiante> perfil = perfilRepository.findFirstByOrderByIdAsc();

        long diasRestantes = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.of(2026, 8, 15));

        // Construir fases dinámicas desde BD
        List<Map<String, Object>> fases = new ArrayList<>();
        int objetivosAtrasados = 0;
        LocalDate hoy = LocalDate.now();

        List<ObjetivoEstudio> todosObjetivos = objetivoRepository.findAllByOrderByNumeroSemanaAsc();

        if (!todosObjetivos.isEmpty()) {
            // Actualizar estados automáticamente
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

            // Agrupar por fase
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

        return "reporte";
    }

    @GetMapping("/init-plan-prueba")
    @ResponseBody
    public String initPlanPrueba() {
        return "Ya no necesario — el plan se genera dinámicamente desde el diagnóstico.";
    }

    @GetMapping("/init-objetivos-prueba")
    @ResponseBody
    public String initObjetivosPrueba() {
        objetivoRepository.deleteAll();
        LocalDate inicio = LocalDate.now();

        Object[][] datos = {
                {"FASE 1 — Fundamentos", 1, "Razonamiento sin fórmulas",
                        "Lógica cotidiana, patrones numéricos, incógnitas sin álgebra",
                        "Que Alexis descubra que ya sabe razonar sin saberlo", 0},
                {"FASE 1 — Fundamentos", 2, "Lectura estratégica",
                        "Textos de interés, idea principal, hechos vs opiniones",
                        "Leer con propósito, no por obligación", 7},
                {"FASE 1 — Fundamentos", 3, "Fracciones aplicadas",
                        "Recetas, descuentos, probabilidades en juegos",
                        "Entender fracciones desde la vida real", 14},
                {"FASE 1 — Fundamentos", 4, "Argumentación básica",
                        "Construir argumentos, detectar falacias, defender opiniones",
                        "Pensar y expresarse con lógica", 21},
                {"FASE 2 — Formalización", 5, "Álgebra con significado",
                        "La x como incógnita, ecuaciones desde problemas reales",
                        "Que el álgebra tenga sentido por fin", 28},
                {"FASE 2 — Formalización", 6, "Comprensión profunda",
                        "Inferencias, resúmenes, textos complejos",
                        "Leer entre líneas y sintetizar ideas", 35},
                {"FASE 3 — Intensificación", 7, "Geometría intuitiva",
                        "Áreas, perímetros, Pitágoras desde casos reales",
                        "Razonamiento espacial aplicado", 42},
                {"FASE 3 — Intensificación", 8, "Análisis crítico",
                        "Textos con posturas opuestas, sesgos, argumentos débiles",
                        "Pensar críticamente ante cualquier información", 49}
        };

        for (Object[] d : datos) {
            ObjetivoEstudio obj = new ObjetivoEstudio();
            obj.setFase((String) d[0]);
            obj.setNumeroSemana((int) d[1]);
            obj.setNombre((String) d[2]);
            obj.setSubtemas((String) d[3]);
            obj.setProposito((String) d[4]);
            obj.setFechaInicio(inicio.plusDays((int) d[5]));
            obj.setFechaFin(inicio.plusDays((int) d[5] + 4));
            obj.setEstado("pendiente");
            objetivoRepository.save(obj);
        }
        return "Objetivos de prueba creados: " + objetivoRepository.count();
    }
}