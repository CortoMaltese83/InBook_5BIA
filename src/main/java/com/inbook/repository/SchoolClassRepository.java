package com.inbook.repository;

import com.inbook.repository.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    Optional<SchoolClass> findById(Long id);
    boolean existsById(Long id);
    void deleteById(Long id);

}
