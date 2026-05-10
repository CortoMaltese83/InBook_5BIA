package com.inbook.repository;

import com.inbook.repository.entity.TeacherInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeacherInvitationRepository extends JpaRepository<TeacherInvitation, Long> {
    Optional<TeacherInvitation> findByToken(String token);

    @Query("select i from TeacherInvitation i where i.institution.id = :institutionId order by i.created_at desc")
    List<TeacherInvitation> findByInstitutionNewestFirst(Long institutionId);

    @Query("select i from TeacherInvitation i order by i.created_at desc")
    List<TeacherInvitation> findAllNewestFirst();
}
