package com.tutor.alexis.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class ObjetivoEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fase;
    private Integer numeroSemana;
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String subtemas;

    @Column(columnDefinition = "TEXT")
    private String proposito;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado; // pendiente, progreso, completado, atrasado
    private LocalDate fechaCompletado;
}