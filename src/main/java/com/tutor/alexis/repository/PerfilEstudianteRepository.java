package com.tutor.alexis.repository;

import com.tutor.alexis.model.PerfilEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PerfilEstudianteRepository extends JpaRepository<PerfilEstudiante, Long> {
    Optional<PerfilEstudiante> findFirstByOrderByIdAsc();
}
