package com.inbook.repository;

import com.inbook.repository.entity.AdminAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {
    @Query("select e from AdminAuditEvent e order by e.created_at desc")
    List<AdminAuditEvent> findLatest(Pageable pageable);

    @Modifying
    @Query("update AdminAuditEvent e set e.actor = null where e.actor.id = :userId")
    int clearActor(@Param("userId") Long userId);

    @Modifying
    @Query("update AdminAuditEvent e set e.targetUser = null where e.targetUser.id = :userId")
    int clearTargetUser(@Param("userId") Long userId);
}
