package com.tutor.alexis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.email.papa}")
    private String emailPapa;

    public void enviarReporteSesion(String reporte, String fecha) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailPapa);
            helper.setSubject("📚 Reporte de Alexis — " + fecha);
            helper.setText(construirHtmlReporte(reporte, fecha), true);

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Error enviando email: " + e.getMessage());
        }
    }

    public void enviarResultadoExamen(String reporte, String fecha, String calificacion) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailPapa);
            helper.setSubject("📊 Examen de Alexis — " + calificacion + " — " + fecha);
            helper.setText(construirHtmlExamen(reporte, fecha), true);

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Error enviando email examen: " + e.getMessage());
        }
    }

    private String construirHtmlReporte(String reporte, String fecha) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: #e94560; color: white; padding: 20px; border-radius: 8px 8px 0 0;">
                    <h1 style="margin:0;">📚 Reporte Diario de Alexis</h1>
                    <p style="margin:8px 0 0 0; opacity:0.8;">%s</p>
                </div>
                <div style="background: white; padding: 20px; border: 1px solid #eee; border-radius: 0 0 8px 8px;">
                    <pre style="background:#f5f7fa; padding:16px; border-radius:8px; white-space:pre-wrap; font-size:14px; color:#333;">%s</pre>
                    <hr style="border:none; border-top:1px solid #eee; margin:20px 0;">
                    <p style="color:#888; font-size:12px;">Ver reporte completo en: <a href="http://localhost:8080/reporte">http://localhost:8080/reporte</a></p>
                </div>
            </body>
            </html>
            """.formatted(fecha, reporte);
    }

    private String construirHtmlExamen(String reporte, String fecha) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background: #0f3460; color: white; padding: 20px; border-radius: 8px 8px 0 0;">
                    <h1 style="margin:0;">📊 Resultado de Examen — Alexis</h1>
                    <p style="margin:8px 0 0 0; opacity:0.8;">%s</p>
                </div>
                <div style="background: white; padding: 20px; border: 1px solid #eee; border-radius: 0 0 8px 8px;">
                    <pre style="background:#f5f7fa; padding:16px; border-radius:8px; white-space:pre-wrap; font-size:14px; color:#333;">%s</pre>
                </div>
            </body>
            </html>
            """.formatted(fecha, reporte);
    }

    public void enviarResumenSemanal(String resumen, String semana) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(emailPapa);
            helper.setSubject("📚 Resumen semanal de Alexis — " + semana);
            helper.setText(construirHtmlSemanal(resumen, semana), true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Error enviando resumen semanal: " + e.getMessage());
        }
    }

    private String construirHtmlSemanal(String resumen, String semana) {
        return """
        <html>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
            <div style="background: #1a1a2e; color: white; padding: 20px; border-radius: 8px 8px 0 0;">
                <h1 style="margin:0; color:#e94560;">📚 Resumen Semanal de Alexis</h1>
                <p style="margin:8px 0 0 0; opacity:0.8;">%s</p>
            </div>
            <div style="background: white; padding: 20px; border: 1px solid #eee; border-radius: 0 0 8px 8px;">
                <pre style="background:#f5f7fa; padding:16px; border-radius:8px; white-space:pre-wrap; font-size:14px; color:#333; line-height:1.6;">%s</pre>
            </div>
        </body>
        </html>
        """.formatted(semana, resumen);
    }
}
