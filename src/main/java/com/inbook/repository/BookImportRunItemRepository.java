package com.inbook.repository;

import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookImportRunItemRepository extends JpaRepository<BookImportRunItem, Long> {
    List<BookImportRunItem> findByRunOrderByIdAsc(BookImportRun run);
}
