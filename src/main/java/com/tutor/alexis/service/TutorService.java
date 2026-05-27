package com.tutor.alexis.service;

import com.tutor.alexis.config.SystemPromptConfig;
import com.tutor.alexis.model.Mensaje;
import com.tutor.alexis.model.PerfilEstudiante;
import com.tutor.alexis.model.Sesion;
import com.tutor.alexis.repository.MensajeRepository;
import com.tutor.alexis.repository.PerfilEstudianteRepository;
import com.tutor.alexis.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TutorService {

    @Autowired private ClaudeService claudeService;
    @Autowired private SystemPromptConfig systemPromptConfig;
    @Autowired private SesionRepository sesionRepository;
    @Autowired private MensajeRepository mensajeRepository;
    @Autowired private PerfilEstudianteRepository perfilRepository;

    // Sesión activa en memoria
    private Long sesionActivaId = null;
    private List<Map<String, String>> historialActivo = new ArrayList<>();

    public String procesarMensaje(String mensajeUsuario) {
        // Iniciar sesión si no hay una activa
        if (sesionActivaId == null) {
            iniciarNuevaSesion();
        }

        // Guardar mensaje del usuario
        guardarMensaje(sesionActivaId, "user", mensajeUsuario);
        historialActivo.add(Map.of("role", "user", "content", mensajeUsuario));

        // Obtener system prompt
        String systemPrompt = obtenerSystemPrompt();

        // Llamar a Claude
        String respuesta = claudeService.enviarConversacion(systemPrompt, historialActivo);

        // Guardar respuesta
        guardarMensaje(sesionActivaId, "assistant", respuesta);
        historialActivo.add(Map.of("role", "assistant", "content", respuesta));

        // Procesar bloques especiales en la respuesta
        procesarBloquesDiagnostico(respuesta);
        procesarBloqueReporte(respuesta);

        return respuesta;
    }

    private void iniciarNuevaSesion() {
        Sesion sesion = new Sesion();
        sesion.setFechaInicio(LocalDateTime.now());
        sesion = sesionRepository.save(sesion);
        sesionActivaId = sesion.getId();
        historialActivo = new ArrayList<>();
    }

    public void cerrarSesion() {
        if (sesionActivaId != null) {
            Optional<Sesion> sesionOpt = sesionRepository.findById(sesionActivaId);
            sesionOpt.ifPresent(sesion -> {
                sesion.setFechaFin(LocalDateTime.now());
                sesionRepository.save(sesion);
            });
            sesionActivaId = null;
            historialActivo = new ArrayList<>();
        }
    }

    private String obtenerSystemPrompt() {
        Optional<PerfilEstudiante> perfilOpt = perfilRepository.findFirstByOrderByIdAsc();
        if (perfilOpt.isPresent() && perfilOpt.get().getDiagnosticoCompletado()) {
            PerfilEstudiante perfil = perfilOpt.get();
            if (perfil.getSystemPromptPersonalizado() != null) {
                return perfil.getSystemPromptPersonalizado();
            }
            return systemPromptConfig.getPromptConPerfil(perfil.getPerfilCompleto());
        }
        return systemPromptConfig.getPromptBase();
    }

    private void procesarBloquesDiagnostico(String respuesta) {
        if (respuesta.contains("PERFIL_ALEXIS_START") && respuesta.contains("PERFIL_ALEXIS_END")) {
            String perfil = extraerBloque(respuesta, "PERFIL_ALEXIS_START", "PERFIL_ALEXIS_END");
            String plan = "";
            if (respuesta.contains("PLAN_ESTUDIOS_START")) {
                plan = extraerBloque(respuesta, "PLAN_ESTUDIOS_START", "PLAN_ESTUDIOS_END");
            }

            PerfilEstudiante estudiante = perfilRepository.findFirstByOrderByIdAsc()
                .orElse(new PerfilEstudiante());
            estudiante.setNombre("Alexis Leonardo");
            estudiante.setEdad(16);
            estudiante.setPerfilCompleto(perfil);
            estudiante.setPlanEstudios(plan);
            estudiante.setDiagnosticoCompletado(true);
            estudiante.setFechaDiagnostico(LocalDateTime.now());
            estudiante.setFechaActualizacion(LocalDateTime.now());
            perfilRepository.save(estudiante);
        }
    }

    private void procesarBloqueReporte(String respuesta) {
        if (respuesta.contains("REPORTE_SESION_START") && sesionActivaId != null) {
            String reporte = extraerBloque(respuesta, "REPORTE_SESION_START", "REPORTE_SESION_END");
            Optional<Sesion> sesionOpt = sesionRepository.findById(sesionActivaId);
            sesionOpt.ifPresent(sesion -> {
                sesion.setReporte(reporte);
                sesionRepository.save(sesion);
            });
        }
    }

    private String extraerBloque(String texto, String inicio, String fin) {
        int idxInicio = texto.indexOf(inicio) + inicio.length();
        int idxFin = texto.indexOf(fin);
        if (idxInicio > 0 && idxFin > idxInicio) {
            return texto.substring(idxInicio, idxFin).trim();
        }
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

    public boolean isDiagnosticoCompletado() {
        return perfilRepository.findFirstByOrderByIdAsc()
            .map(PerfilEstudiante::getDiagnosticoCompletado)
            .orElse(false);
    }

    public Long getSesionActivaId() {
        return sesionActivaId;
    }
}
