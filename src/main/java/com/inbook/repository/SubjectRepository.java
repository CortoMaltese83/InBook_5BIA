package com.inbook.repository;

import com.inbook.repository.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByNomeMateria(String nomeMateria);

    List<Subject> findByClasse_Id(Long classeId);

    List<Subject> findByDocente_Id(Long docenteId);

    List<Subject> findByClasse_IdAndDocente_Id(Long classeId, Long docenteId);
}