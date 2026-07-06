package com.tutor.alexis.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class LeccionCompletada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer numeroSemana;
    private Integer numeroLeccion;
    private LocalDate fecha;
    private String tema;
    private Integer nivelComprension;

    /**
     * ID de la sesion que genero esta leccion. Hibernate crea la columna
     * automaticamente (ddl-auto update). Lecciones anteriores a este campo
     * quedan en null — decidido, igual que las "Leccion 0".
     */
    private Long sesionId;

    @Column(columnDefinition = "TEXT")
    private String logro;

    @Column(columnDefinition = "TEXT")
    private String areaReforzar;
}