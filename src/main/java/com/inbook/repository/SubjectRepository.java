package com.inbook.repository;

import com.inbook.repository.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByNomeMateria(String nomeMateria);

    List<Subject> findByClasse_Id(Long classeId);

    List<Subject> findByDocente_Id(Long docenteId);

    List<Subject> findByClasse_IdAndDocente_Id(Long classeId, Long docenteId);

    boolean existsByBook_Isbn(String isbn);

    @Query("""
            select s from Subject s
            join s.classe c
            left join c.institution i
            left join c.docente cd
            left join cd.institution cdi
            left join s.docente d
            left join s.book b
            where (:classeId is null or c.id = :classeId)
              and (
                :institutionId is null
                or i.id = :institutionId
                or (i.id is null and cdi.id = :institutionId)
              )
              and (
                :bookStatus is null or :bookStatus = ''
                or (:bookStatus = 'with_book' and s.book is not null)
                or (:bookStatus = 'without_book' and s.book is null)
              )
              and (
                :search is null or :search = ''
                or lower(s.nomeMateria) like lower(concat('%', :search, '%'))
                or lower(c.nome) like lower(concat('%', :search, '%'))
                or lower(c.anno) like lower(concat('%', :search, '%'))
                or lower(c.sezione) like lower(concat('%', :search, '%'))
                or lower(d.name) like lower(concat('%', :search, '%'))
                or lower(d.surname) like lower(concat('%', :search, '%'))
                or lower(d.username) like lower(concat('%', :search, '%'))
                or lower(b.isbn) like lower(concat('%', :search, '%'))
                or lower(b.autore) like lower(concat('%', :search, '%'))
                or lower(b.titolo) like lower(concat('%', :search, '%'))
                or lower(b.casaEditrice) like lower(concat('%', :search, '%'))
              )
            """)
    Page<Subject> searchSubjects(@Param("classeId") Long classeId,
                                 @Param("institutionId") Long institutionId,
                                 @Param("search") String search,
                                 @Param("bookStatus") String bookStatus,
                                 Pageable pageable);

    @Query("""
            select s from Subject s
            join s.classe c
            left join c.institution i
            left join c.docente cd
            left join cd.institution cdi
            join s.book b
            where lower(c.stato) = 'active'
              and (
                i.id = :institutionId
                or (i.id is null and cdi.id = :institutionId)
              )
            order by c.anno asc, c.sezione asc, c.nome asc, s.nomeMateria asc, b.titolo asc
            """)
    List<Subject> findActiveBookAssignmentsByInstitution(@Param("institutionId") Long institutionId);
}
