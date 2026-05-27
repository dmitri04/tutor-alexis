package com.tutor.alexis.repository;

import com.tutor.alexis.model.ObjetivoEstudio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ObjetivoEstudioRepository extends JpaRepository<ObjetivoEstudio, Long> {
    List<ObjetivoEstudio> findAllByOrderByNumeroSemanaAsc();
    List<ObjetivoEstudio> findByEstado(String estado);
}