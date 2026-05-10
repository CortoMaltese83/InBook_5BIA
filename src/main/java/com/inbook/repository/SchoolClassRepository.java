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
            left join c.institution i
            left join d.institution di
            where (:stato is null or :stato = '' or c.stato = :stato)
              and (
                :institutionId is null
                or i.id = :institutionId
                or (i.id is null and di.id = :institutionId)
              )
              and (
                :search is null or :search = ''
                or lower(c.nome) like lower(concat('%', :search, '%'))
                or lower(c.anno) like lower(concat('%', :search, '%'))
                or lower(c.sezione) like lower(concat('%', :search, '%'))
                or lower(i.name) like lower(concat('%', :search, '%'))
                or lower(i.code) like lower(concat('%', :search, '%'))
                or lower(di.name) like lower(concat('%', :search, '%'))
                or lower(di.code) like lower(concat('%', :search, '%'))
                or lower(d.name) like lower(concat('%', :search, '%'))
                or lower(d.surname) like lower(concat('%', :search, '%'))
                or lower(d.username) like lower(concat('%', :search, '%'))
              )
            """)
    Page<SchoolClass> searchClasses(@Param("search") String search,
                                    @Param("institutionId") Long institutionId,
                                    @Param("stato") String stato,
                                    Pageable pageable);
}
