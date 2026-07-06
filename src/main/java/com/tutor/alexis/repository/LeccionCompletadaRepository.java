package com.tutor.alexis.repository;

import com.tutor.alexis.model.LeccionCompletada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeccionCompletadaRepository extends JpaRepository<LeccionCompletada, Long> {

    List<LeccionCompletada> findAllByOrderByFechaDescNumeroLeccionDesc();

    // Para el guardado idempotente de lecciones (una leccion por sesion)
    Optional<LeccionCompletada> findFirstBySesionId(Long sesionId);
}