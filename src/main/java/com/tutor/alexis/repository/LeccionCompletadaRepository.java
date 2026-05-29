package com.tutor.alexis.repository;

import com.tutor.alexis.model.LeccionCompletada;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeccionCompletadaRepository extends JpaRepository<LeccionCompletada, Long> {
    List<LeccionCompletada> findByNumeroSemanaOrderByNumeroLeccionAsc(Integer semana);
    List<LeccionCompletada> findAllByOrderByFechaDescNumeroLeccionDesc();
}