package com.inbook.repository;

import com.inbook.repository.entity.AdminAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {
    @Query("select e from AdminAuditEvent e order by e.created_at desc")
    List<AdminAuditEvent> findLatest(Pageable pageable);
}
