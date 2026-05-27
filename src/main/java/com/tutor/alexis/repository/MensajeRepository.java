package com.tutor.alexis.repository;

import com.tutor.alexis.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findBySesionIdOrderByTimestamp(Long sesionId);
}
