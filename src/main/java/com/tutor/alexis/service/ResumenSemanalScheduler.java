package com.tutor.alexis.service;

import com.tutor.alexis.model.Sesion;
import com.tutor.alexis.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ResumenSemanalScheduler {

    @Autowired private SesionRepository sesionRepository;
    @Autowired private ClaudeService claudeService;
    @Autowired private EmailService emailService;

    @Scheduled(cron = "0 0 20 * * FRI")
    public void enviarResumenSemanal() {
        List<Sesion> sesiones = sesionRepository
                .findByFechaInicioAfterOrderByFechaInicioDesc(
                        LocalDateTime.now().minusDays(7));

        if (sesiones.isEmpty()) return;

        StringBuilder reportes = new StringBuilder();
        for (Sesion s : sesiones) {
            if (s.getReporte() != null) {
                reportes.append(s.getReporte()).append("\n---\n");
            }
        }

        if (reportes.length() == 0) return;

        String prompt = "Eres el tutor de Alexis. Basándote en estos reportes de la semana, " +
                "genera un resumen ejecutivo para su mamá con:\n" +
                "- Qué aprendió esta semana\n" +
                "- Cómo fue su actitud general\n" +
                "- Sus logros más importantes\n" +
                "- Qué necesita reforzar la próxima semana\n" +
                "- Una calificación general de la semana del 1 al 10\n" +
                "- Recomendación para los papás\n\n" +
                "Reportes de la semana:\n" + reportes;

        String resumen = claudeService.enviarConversacionCompleta(
                "Eres el tutor personal de Alexis Leonardo.",
                List.of(Map.of("role", "user", "content", prompt))
        );

        String semana = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern(
                        "dd 'de' MMMM 'de' yyyy", new Locale("es", "MX")));

        emailService.enviarResumenSemanal(resumen, semana);
    }
}