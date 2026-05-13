package com.inbook.repository;

import com.inbook.repository.entity.BookImportRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookImportRunRepository extends JpaRepository<BookImportRun, Long> {
    @Query("select r from BookImportRun r order by r.started_at desc")
    List<BookImportRun> findLatest(Pageable pageable);
}
