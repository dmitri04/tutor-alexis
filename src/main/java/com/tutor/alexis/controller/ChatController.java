package com.tutor.alexis.controller;

import com.tutor.alexis.service.TutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class ChatController {

    @Autowired
    private TutorService tutorService;

    @GetMapping("/")
    public String chat(Model model) {
        model.addAttribute("diagnosticoCompletado", tutorService.isDiagnosticoCompletado());
        return "chat";
    }

    @PostMapping("/chat")
    @ResponseBody
    public Map<String, String> enviarMensaje(@RequestBody Map<String, String> request) {
        String mensaje = request.get("mensaje");
        String respuesta = tutorService.procesarMensaje(mensaje);
        return Map.of("respuesta", respuesta);
    }

    @PostMapping("/cerrar-sesion")
    @ResponseBody
    public Map<String, String> cerrarSesion() {
        tutorService.cerrarSesion();
        return Map.of("status", "ok");
    }
}
