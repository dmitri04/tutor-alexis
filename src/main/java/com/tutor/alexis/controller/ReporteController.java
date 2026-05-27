package com.tutor.alexis.controller;

import com.tutor.alexis.model.PerfilEstudiante;
import com.tutor.alexis.model.Sesion;
import com.tutor.alexis.repository.PerfilEstudianteRepository;
import com.tutor.alexis.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
public class ReporteController {

    @Autowired private SesionRepository sesionRepository;
    @Autowired private PerfilEstudianteRepository perfilRepository;

    @GetMapping("/reporte")
    public String reporte(Model model) {
        // Sesiones de los últimos 7 días
        List<Sesion> sesiones = sesionRepository
            .findByFechaInicioAfterOrderByFechaInicioDesc(LocalDateTime.now().minusDays(7));

        // Sesión de hoy
        Optional<Sesion> sesionHoy = sesionRepository
            .findByFechaInicioAfterOrderByFechaInicioDesc(LocalDateTime.now().withHour(0).withMinute(0))
            .stream().findFirst();

        // Perfil del estudiante
        Optional<PerfilEstudiante> perfil = perfilRepository.findFirstByOrderByIdAsc();

        model.addAttribute("sesiones", sesiones);
        model.addAttribute("sesionHoy", sesionHoy.orElse(null));
        model.addAttribute("perfil", perfil.orElse(null));
        model.addAttribute("totalSesiones", sesionRepository.count());

        return "reporte";
    }
}
