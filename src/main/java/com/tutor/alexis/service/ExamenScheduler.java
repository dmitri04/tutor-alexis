package com.tutor.alexis.service;

import com.tutor.alexis.model.Sesion;
import com.tutor.alexis.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExamenScheduler {

    @Autowired private SesionRepository sesionRepository;
    @Autowired private TutorService tutorService;
    @Autowired private EmailService emailService;

    // Verifica cada día a las 9am si toca examen
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void verificarExamenAutomatico() {
        List<Sesion> sesiones = sesionRepository.findAllByOrderByFechaInicioDesc();

        if (sesiones.size() < 3) return;

        // Contar sesiones desde último examen
        long sesionesDesdeExamen = sesiones.stream()
            .takeWhile(s -> s.getReporte() == null || !s.getReporte().contains("REPORTE_EXAMEN"))
            .count();

        if (sesionesDesdeExamen >= 3) {
            // Inyectar mensaje de examen automático
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            tutorService.procesarMensaje(
                "EXAMEN_AUTOMATICO — Han pasado 3 sesiones, genera el examen correspondiente de forma natural, " +
                "dile a Alexis que es momento de su evaluación y arranca con las preguntas."
            );
        }
    }
}
