package com.inbook.repository;

import com.inbook.repository.entity.BookLookupCache;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookLookupCacheRepository extends JpaRepository<BookLookupCache, String> {
    @Query("""
            select c from BookLookupCache c
            where (:search is not null and :search <> '' and (
                lower(c.isbn) like lower(concat('%', :search, '%'))
                or lower(c.titolo) like lower(concat('%', :search, '%'))
                or lower(c.autore) like lower(concat('%', :search, '%'))
                or lower(c.casaEditrice) like lower(concat('%', :search, '%'))
                or lower(c.source) like lower(concat('%', :search, '%'))
            ))
            or (:normalizedIsbn is not null and :normalizedIsbn <> '' and c.isbn like concat('%', :normalizedIsbn, '%'))
            order by c.updated_at desc
            """)
    List<BookLookupCache> searchCache(@Param("search") String search,
                                      @Param("normalizedIsbn") String normalizedIsbn,
                                      Pageable pageable);
}
