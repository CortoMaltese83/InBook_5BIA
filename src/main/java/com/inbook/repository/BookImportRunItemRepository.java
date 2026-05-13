package com.inbook.repository;

import com.inbook.dto.BookImportErrorGroup;
import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookImportRunItemRepository extends JpaRepository<BookImportRunItem, Long> {
    List<BookImportRunItem> findByRunOrderByIdAsc(BookImportRun run);

    @Query("""
            select new com.inbook.dto.BookImportErrorGroup(i.status, i.fallbackStep, i.reason, count(i))
            from BookImportRunItem i
            where i.run = :run
              and i.status = 'DISCARDED'
            group by i.status, i.fallbackStep, i.reason
            order by count(i) desc, i.fallbackStep asc, i.reason asc
            """)
    List<BookImportErrorGroup> findDiscardedGroups(@Param("run") BookImportRun run);

    @Query("""
            select i from BookImportRunItem i
            where i.run = :run
              and i.status = :status
              and ((:fallbackStep is null and i.fallbackStep is null) or i.fallbackStep = :fallbackStep)
              and ((:reason is null and i.reason is null) or i.reason = :reason)
            order by i.id asc
            """)
    List<BookImportRunItem> findByDiscardedGroup(@Param("run") BookImportRun run,
                                                 @Param("status") String status,
                                                 @Param("fallbackStep") String fallbackStep,
                                                 @Param("reason") String reason);
}
