package com.inbook.repository;

import com.inbook.repository.entity.SchoolClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    Optional<SchoolClass> findById(Long id);
    boolean existsById(Long id);
    void deleteById(Long id);

    @Query("""
            select c from SchoolClass c
            left join c.docente d
            where (:stato is null or :stato = '' or c.stato = :stato)
              and (
                :search is null or :search = ''
                or lower(c.nome) like lower(concat('%', :search, '%'))
                or lower(c.anno) like lower(concat('%', :search, '%'))
                or lower(c.sezione) like lower(concat('%', :search, '%'))
                or lower(d.name) like lower(concat('%', :search, '%'))
                or lower(d.surname) like lower(concat('%', :search, '%'))
                or lower(d.username) like lower(concat('%', :search, '%'))
              )
            """)
    Page<SchoolClass> searchClasses(@Param("search") String search,
                                    @Param("stato") String stato,
                                    Pageable pageable);
}
