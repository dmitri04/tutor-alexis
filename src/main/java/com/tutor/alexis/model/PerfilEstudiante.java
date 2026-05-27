package com.tutor.alexis.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class PerfilEstudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private Integer edad;

    @Column(columnDefinition = "TEXT")
    private String perfilCompleto; // generado por diagnóstico

    @Column(columnDefinition = "TEXT")
    private String planEstudios; // generado por diagnóstico

    @Column(columnDefinition = "TEXT")
    private String systemPromptPersonalizado; // prompt reformulado post-diagnóstico

    private Boolean diagnosticoCompletado = false;
    private LocalDateTime fechaDiagnostico;
    private LocalDateTime fechaActualizacion;
}
