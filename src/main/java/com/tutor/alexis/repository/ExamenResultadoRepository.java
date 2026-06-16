package com.tutor.alexis.repository;

import com.tutor.alexis.model.ExamenResultado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExamenResultadoRepository extends JpaRepository<ExamenResultado, Long> {
    List<ExamenResultado> findAllByOrderByFechaDesc();

    // Para guardado idempotente: un examen por dia. Si ya existe el de hoy,
    // se actualiza en vez de crear uno nuevo (evita choque de PK).
    Optional<ExamenResultado> findFirstByFecha(LocalDate fecha);
}