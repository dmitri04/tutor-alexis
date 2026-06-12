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

    @Column(length = 500)
    private String calificacion;

    @Column(length = 2000)
    private String matematicas;

    @Column(length = 2000)
    private String verbal;
    private Boolean reprobado;

    @Column(length = 2000)
    private String erroresClave;

    @Column(length = 2000)
    private String recomendacion;
}