package com.inbook.repository;

import com.inbook.repository.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmailVerificationToken(String emailVerificationToken);
    Optional<AppUser> findByPasswordResetTokenHash(String passwordResetTokenHash);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email); //creati per controllare l'esistenza di email e username nel db
    List<AppUser> findByInstitution_IdOrderBySurnameAscNameAsc(Long institutionId);
    List<AppUser> findAllByOrderBySurnameAscNameAsc();

}
