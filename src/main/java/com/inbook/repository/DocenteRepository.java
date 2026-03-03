package com.inbook.repository;

import com.inbook.repository.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
public interface DocenteRepository extends JpaRepository<SchoolClass, Long> {
    //List<SchoolClass> findByDocenteId(Long id);
}
