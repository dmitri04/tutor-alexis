package com.tutor.alexis.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class ExamenResultado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fecha;
    private String calificacion;
    private String matematicas;
    private String verbal;
    private Boolean reprobado;

    @Column(columnDefinition = "TEXT")
    private String erroresClave;

    @Column(columnDefinition = "TEXT")
    private String recomendacion;
}