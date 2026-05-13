package com.inbook.repository;

import com.inbook.repository.entity.BookImportRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookImportRunRepository extends JpaRepository<BookImportRun, Long> {
    @Query("select r from BookImportRun r order by r.started_at desc")
    List<BookImportRun> findLatest(Pageable pageable);

    @Query("select r from BookImportRun r where r.type = :type and upper(r.status) in :statuses order by r.started_at desc")
    List<BookImportRun> findActiveByType(@Param("type") String type,
                                         @Param("statuses") List<String> statuses,
                                         Pageable pageable);
}
