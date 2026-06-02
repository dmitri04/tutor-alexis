package com.tutor.alexis.repository;

import com.tutor.alexis.model.ExamenResultado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamenResultadoRepository extends JpaRepository<ExamenResultado, Long> {
    List<ExamenResultado> findAllByOrderByFechaDesc();
}