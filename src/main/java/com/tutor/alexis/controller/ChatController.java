package com.tutor.alexis.controller;

import com.tutor.alexis.service.TutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@Controller
public class ChatController {

    @Autowired
    private TutorService tutorService;

    @GetMapping("/")
    public String chat(Model model) {
        model.addAttribute("diagnosticoCompletado", tutorService.isDiagnosticoCompletado());
        model.addAttribute("totalSesiones", tutorService.getTotalSesiones());
        return "chat";
    }

    @PostMapping("/chat")
    @ResponseBody
    public Map<String, Object> enviarMensaje(
            @RequestParam("mensaje") String mensaje,
            @RequestParam(value = "imagen", required = false) MultipartFile imagen) {

        try {
            String respuesta;
            if (imagen != null && !imagen.isEmpty()) {
                String base64 = Base64.getEncoder().encodeToString(imagen.getBytes());
                String mediaType = imagen.getContentType();
                respuesta = tutorService.procesarMensajeConImagen(mensaje, base64, mediaType);
            } else {
                respuesta = tutorService.procesarMensaje(mensaje);
            }
            return Map.of(
                "respuesta", respuesta,
                "tiempoSesion", tutorService.getTiempoSesionMinutos()
            );
        } catch (Exception e) {
            return Map.of("respuesta", "Error: " + e.getMessage(), "tiempoSesion", 0);
        }
    }

    @PostMapping("/cerrar-sesion")
    @ResponseBody
    public Map<String, Object> cerrarSesion() {
        return tutorService.cerrarSesion();
    }

    @GetMapping("/estado")
    @ResponseBody
    public Map<String, Object> estado() {
        return Map.of(
            "tiempoSesion", tutorService.getTiempoSesionMinutos(),
            "sesionActiva", tutorService.getSesionActivaId() != null,
            "totalSesiones", tutorService.getTotalSesiones()
        );
    }
}

