package com.inbook.repository;

import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunErrorGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookImportRunErrorGroupRepository extends JpaRepository<BookImportRunErrorGroup, Long> {
    List<BookImportRunErrorGroup> findByRunOrderByItemCountDescFallbackStepAscReasonAsc(BookImportRun run);

    Optional<BookImportRunErrorGroup> findByRunAndStatusAndFallbackStepAndReason(BookImportRun run,
                                                                                String status,
                                                                                String fallbackStep,
                                                                                String reason);
}
