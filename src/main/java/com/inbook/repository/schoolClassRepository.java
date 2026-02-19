package com.inbook.repository;

import com.inbook.repository.entity.schoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
public interface schoolClassRepository extends JpaRepository<schoolClass, Long> {

    Optional<schoolClass> findById(Long id);
    boolean existsById(Long id);
    void deleteById(Long id);

}
