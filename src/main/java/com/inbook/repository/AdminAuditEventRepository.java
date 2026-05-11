package com.inbook.repository;

import com.inbook.repository.entity.AdminAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {
    @Query("select e from AdminAuditEvent e order by e.created_at desc")
    List<AdminAuditEvent> findLatest(Pageable pageable);

    @Query("""
            select e from AdminAuditEvent e
            left join e.actor actor
            left join e.targetUser targetUser
            left join e.institution institution
            where (:institutionId is null or institution.id = :institutionId)
              and (:action is null or :action = '' or e.action = :action)
              and (
                :search is null or :search = ''
                or lower(e.action) like lower(concat('%', :search, '%'))
                or lower(e.details) like lower(concat('%', :search, '%'))
                or lower(actor.username) like lower(concat('%', :search, '%'))
                or lower(actor.email) like lower(concat('%', :search, '%'))
                or lower(targetUser.username) like lower(concat('%', :search, '%'))
                or lower(targetUser.email) like lower(concat('%', :search, '%'))
                or lower(targetUser.name) like lower(concat('%', :search, '%'))
                or lower(targetUser.surname) like lower(concat('%', :search, '%'))
                or lower(institution.name) like lower(concat('%', :search, '%'))
                or lower(institution.code) like lower(concat('%', :search, '%'))
              )
            order by e.created_at desc
            """)
    Page<AdminAuditEvent> searchAuditEvents(@Param("search") String search,
                                            @Param("action") String action,
                                            @Param("institutionId") Long institutionId,
                                            Pageable pageable);

    @Query("select distinct e.action from AdminAuditEvent e order by e.action asc")
    List<String> findDistinctActions();

    @Modifying
    @Query("update AdminAuditEvent e set e.actor = null where e.actor.id = :userId")
    int clearActor(@Param("userId") Long userId);

    @Modifying
    @Query("update AdminAuditEvent e set e.targetUser = null where e.targetUser.id = :userId")
    int clearTargetUser(@Param("userId") Long userId);
}
