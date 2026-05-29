package com.tutor.alexis.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String tema;
    private Integer nivelComprension;
    private String actitud;
    private String logroDelDia;
    private String areaReforzar;
    private String objetivoSiguiente;
    @Column(columnDefinition = "TEXT")
    private String reporte;
    @Column(columnDefinition = "TEXT")
    private String historialJson;
    private Boolean cierreVoluntario = false;

    @OneToMany(mappedBy = "sesion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Mensaje> mensajes = new ArrayList<>();
}
