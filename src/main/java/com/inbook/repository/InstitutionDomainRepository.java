package com.inbook.repository;

import com.inbook.repository.entity.InstitutionDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstitutionDomainRepository extends JpaRepository<InstitutionDomain, Long> {
    Optional<InstitutionDomain> findByDomain(String domain);
    List<InstitutionDomain> findByInstitution_IdOrderByDomainAsc(Long institutionId);
    List<InstitutionDomain> findAllByOrderByDomainAsc();
}
