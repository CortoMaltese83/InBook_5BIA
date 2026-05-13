package com.inbook.repository;

import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookImportRunSourceRepository extends JpaRepository<BookImportRunSource, Long> {
    List<BookImportRunSource> findByRunOrderByIdAsc(BookImportRun run);
}
