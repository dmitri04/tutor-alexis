package com.tutor.alexis.repository;

import com.tutor.alexis.model.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SesionRepository extends JpaRepository<Sesion, Long> {
    List<Sesion> findByFechaInicioAfterOrderByFechaInicioDesc(LocalDateTime fecha);
    List<Sesion> findAllByOrderByFechaInicioDesc();
}
