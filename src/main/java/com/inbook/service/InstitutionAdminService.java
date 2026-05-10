package com.inbook.service;

import com.inbook.repository.*;
import com.inbook.repository.entity.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class InstitutionAdminService {
    public static final String USER_EMAIL_PENDING = "EMAIL_PENDING";
    public static final String USER_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String USER_ACTIVE = "ACTIVE";
    public static final String USER_SUSPENDED = "SUSPENDED";

    private static final String INVITE_PENDING = "PENDING";
    private static final String INVITE_ACCEPTED = "ACCEPTED";
    private static final String INVITE_REVOKED = "REVOKED";
    private static final long VERIFY_TOKEN_TTL_MS = 24L * 60 * 60 * 1000;
    private static final long INVITE_TTL_MS = 14L * 24 * 60 * 60 * 1000;

    private final InstitutionRepository institutionRepository;
    private final InstitutionDomainRepository domainRepository;
    private final TeacherInvitationRepository invitationRepository;
    private final AdminAuditEventRepository auditRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public InstitutionAdminService(InstitutionRepository institutionRepository,
                                   InstitutionDomainRepository domainRepository,
                                   TeacherInvitationRepository invitationRepository,
                                   AdminAuditEventRepository auditRepository,
                                   AppUserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   NotificationService notificationService) {
        this.institutionRepository = institutionRepository;
        this.domainRepository = domainRepository;
        this.invitationRepository = invitationRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    public AppUser requireLoggedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("Utente non autenticato");
        }
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: " + principal.getName()));
    }

    public boolean isAdmin(AppUser user) {
        return user != null && user.getRoles() != null && user.getRoles().toUpperCase(Locale.ITALIAN).contains("ADMIN");
    }

    public List<Institution> listInstitutions() {
        return institutionRepository.findAllByOrderByNameAsc();
    }

    public List<InstitutionDomain> listDomains() {
        return domainRepository.findAllByOrderByDomainAsc();
    }

    public List<AppUser> listTeachers() {
        return userRepository.findAllByOrderBySurnameAscNameAsc().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().toUpperCase(Locale.ITALIAN).contains("DOCENTE"))
                .toList();
    }

    public List<TeacherInvitation> listInvitations() {
        return invitationRepository.findAllNewestFirst();
    }

    public List<AdminAuditEvent> latestAuditEvents() {
        return auditRepository.findLatest(PageRequest.of(0, 100));
    }

    @Transactional
    public Institution createInstitution(String name, String code, Principal principal) {
        AppUser actor = requireAdmin(principal);
        String cleanName = requireText(name, "Nome istituto");
        String cleanCode = normalizeCode(code);
        if (institutionRepository.existsByCode(cleanCode)) {
            throw new IllegalArgumentException("Codice istituto gia presente");
        }

        long now = System.currentTimeMillis();
        Institution institution = new Institution();
        institution.setName(cleanName);
        institution.setCode(cleanCode);
        institution.setStatus("ACTIVE");
        institution.setCreated_at(now);
        institution.setUpdated_at(now);
        Institution saved = institutionRepository.save(institution);
        audit(actor, saved, null, "INSTITUTION_CREATE", cleanName + " (" + cleanCode + ")");
        return saved;
    }

    @Transactional
    public void updateInstitution(Long id, String name, String code, String status, Principal principal) {
        AppUser actor = requireAdmin(principal);
        Institution institution = requireInstitution(id);
        institution.setName(requireText(name, "Nome istituto"));
        institution.setCode(normalizeCode(code));
        institution.setStatus(normalizeStatus(status, "ACTIVE", List.of("ACTIVE", "SUSPENDED")));
        institution.setUpdated_at(System.currentTimeMillis());
        institutionRepository.save(institution);
        audit(actor, institution, null, "INSTITUTION_UPDATE", institution.getName());
    }

    @Transactional
    public InstitutionDomain addDomain(Long institutionId, String domain, Principal principal) {
        AppUser actor = requireAdmin(principal);
        Institution institution = requireInstitution(institutionId);
        String cleanDomain = normalizeDomain(domain);
        Optional<InstitutionDomain> existing = domainRepository.findByDomain(cleanDomain);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Dominio gia censito");
        }

        long now = System.currentTimeMillis();
        InstitutionDomain newDomain = new InstitutionDomain();
        newDomain.setInstitution(institution);
        newDomain.setDomain(cleanDomain);
        newDomain.setActive(true);
        newDomain.setCreated_at(now);
        newDomain.setUpdated_at(now);
        InstitutionDomain saved = domainRepository.save(newDomain);
        audit(actor, institution, null, "DOMAIN_ADD", cleanDomain);
        return saved;
    }

    @Transactional
    public void toggleDomain(Long id, boolean active, Principal principal) {
        AppUser actor = requireAdmin(principal);
        InstitutionDomain domain = domainRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dominio non trovato"));
        domain.setActive(active);
        domain.setUpdated_at(System.currentTimeMillis());
        domainRepository.save(domain);
        audit(actor, domain.getInstitution(), null, active ? "DOMAIN_ENABLE" : "DOMAIN_DISABLE", domain.getDomain());
    }

    @Transactional
    public TeacherInvitation inviteTeacher(Long institutionId, String email, Principal principal) {
        AppUser actor = requireAdmin(principal);
        Institution institution = requireInstitution(institutionId);
        if (!"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
            throw new IllegalArgumentException("Istituto non attivo");
        }

        String cleanEmail = normalizeEmail(email);
        long now = System.currentTimeMillis();
        TeacherInvitation invitation = new TeacherInvitation();
        invitation.setInstitution(institution);
        invitation.setEmail(cleanEmail);
        invitation.setToken(generateToken());
        invitation.setStatus(INVITE_PENDING);
        invitation.setInvitedBy(actor);
        invitation.setCreated_at(now);
        invitation.setExpires_at(now + INVITE_TTL_MS);
        TeacherInvitation saved = invitationRepository.save(invitation);
        audit(actor, institution, null, "INVITE_CREATE", cleanEmail);
        notificationService.sendInvitationEmail(saved);
        return saved;
    }

    @Transactional
    public void revokeInvitation(Long invitationId, Principal principal) {
        AppUser actor = requireAdmin(principal);
        TeacherInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invito non trovato"));
        invitation.setStatus(INVITE_REVOKED);
        invitation.setRevoked_at(System.currentTimeMillis());
        invitationRepository.save(invitation);
        audit(actor, invitation.getInstitution(), null, "INVITE_REVOKE", invitation.getEmail());
    }

    @Transactional
    public AppUser registerTeacher(String email, String rawPassword, String username, String name, String surname, String inviteToken) {
        String cleanEmail = normalizeEmail(email);
        String cleanUsername = requireText(username, "Username");
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("Email gia registrata");
        }
        if (userRepository.existsByUsername(cleanUsername)) {
            throw new IllegalArgumentException("Username gia registrato");
        }

        TeacherInvitation invitation = resolveInvitation(inviteToken, cleanEmail).orElse(null);
        Institution institution = invitation != null
                ? invitation.getInstitution()
                : resolveInstitutionByEmailDomain(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("Dominio email non autorizzato"));
        if (!"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
            throw new IllegalArgumentException("Istituto non attivo");
        }

        AppUser user = new AppUser();
        user.setEmail(cleanEmail);
        user.setPasswordHash(passwordEncoder.encode(requireText(rawPassword, "Password")));
        user.setUsername(cleanUsername);
        user.setName(requireText(name, "Nome"));
        user.setSurname(requireText(surname, "Cognome"));
        user.setRoles("TYPE_DOCENTE");
        user.setEnabled(false);
        user.setInstitution(institution);
        user.setStatus(USER_EMAIL_PENDING);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(generateToken());
        user.setEmailVerificationExpiresAt(System.currentTimeMillis() + VERIFY_TOKEN_TTL_MS);
        user.setRegistrationInvitation(invitation);
        AppUser saved = userRepository.save(user);

        audit(null, institution, saved, "TEACHER_REGISTER", cleanEmail);
        notificationService.sendVerificationEmail(saved);
        return saved;
    }

    @Transactional
    public AppUser verifyEmail(String token) {
        String cleanToken = requireText(token, "Token");
        AppUser user = userRepository.findByEmailVerificationToken(cleanToken)
                .orElseThrow(() -> new IllegalArgumentException("Token non valido"));
        Long expiresAt = user.getEmailVerificationExpiresAt();
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            throw new IllegalArgumentException("Token scaduto");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);

        TeacherInvitation invitation = user.getRegistrationInvitation();
        if (invitation != null && INVITE_PENDING.equals(invitation.getStatus()) && invitation.getExpires_at() >= System.currentTimeMillis()) {
            user.setStatus(USER_ACTIVE);
            user.setEnabled(true);
            invitation.setStatus(INVITE_ACCEPTED);
            invitation.setAccepted_at(System.currentTimeMillis());
            invitationRepository.save(invitation);
            audit(null, user.getInstitution(), user, "TEACHER_VERIFY_INVITED", user.getEmail());
        } else {
            user.setStatus(USER_PENDING_APPROVAL);
            user.setEnabled(false);
            audit(null, user.getInstitution(), user, "TEACHER_VERIFY_PENDING_APPROVAL", user.getEmail());
        }

        return userRepository.save(user);
    }

    @Transactional
    public void approveTeacher(Long userId, Principal principal) {
        AppUser actor = requireAdmin(principal);
        AppUser user = requireUser(userId);
        user.setStatus(USER_ACTIVE);
        user.setEnabled(true);
        userRepository.save(user);
        audit(actor, user.getInstitution(), user, "TEACHER_APPROVE", user.getEmail());
    }

    @Transactional
    public void suspendTeacher(Long userId, Principal principal) {
        AppUser actor = requireAdmin(principal);
        AppUser user = requireUser(userId);
        user.setStatus(USER_SUSPENDED);
        user.setEnabled(false);
        userRepository.save(user);
        audit(actor, user.getInstitution(), user, "TEACHER_SUSPEND", user.getEmail());
    }

    @Transactional
    public void reactivateTeacher(Long userId, Principal principal) {
        AppUser actor = requireAdmin(principal);
        AppUser user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email docente non verificata");
        }
        user.setStatus(USER_ACTIVE);
        user.setEnabled(true);
        userRepository.save(user);
        audit(actor, user.getInstitution(), user, "TEACHER_REACTIVATE", user.getEmail());
    }

    public Optional<TeacherInvitation> findInvitationByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return invitationRepository.findByToken(token.trim())
                .filter(invitation -> INVITE_PENDING.equals(invitation.getStatus()))
                .filter(invitation -> invitation.getExpires_at() != null && invitation.getExpires_at() >= System.currentTimeMillis());
    }

    private Optional<TeacherInvitation> resolveInvitation(String token, String email) {
        return findInvitationByToken(token)
                .filter(invitation -> invitation.getEmail().equalsIgnoreCase(email));
    }

    private Optional<Institution> resolveInstitutionByEmailDomain(String email) {
        String domain = email.substring(email.indexOf('@') + 1);
        return domainRepository.findByDomain(domain)
                .filter(InstitutionDomain::isActive)
                .map(InstitutionDomain::getInstitution)
                .filter(institution -> "ACTIVE".equalsIgnoreCase(institution.getStatus()));
    }

    private AppUser requireAdmin(Principal principal) {
        AppUser user = requireLoggedUser(principal);
        if (!isAdmin(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        return user;
    }

    private Institution requireInstitution(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Istituto mancante");
        }
        return institutionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Istituto non trovato"));
    }

    private AppUser requireUser(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Utente mancante");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));
    }

    private void audit(AppUser actor, Institution institution, AppUser targetUser, String action, String details) {
        AdminAuditEvent event = new AdminAuditEvent();
        event.setActor(actor);
        event.setInstitution(institution);
        event.setTargetUser(targetUser);
        event.setAction(action);
        event.setDetails(details);
        event.setCreated_at(System.currentTimeMillis());
        auditRepository.save(event);
    }

    private String normalizeEmail(String email) {
        String cleanEmail = requireText(email, "Email").toLowerCase(Locale.ITALIAN);
        if (!cleanEmail.contains("@") || cleanEmail.startsWith("@") || cleanEmail.endsWith("@")) {
            throw new IllegalArgumentException("Email non valida");
        }
        return cleanEmail;
    }

    private String normalizeDomain(String domain) {
        String cleanDomain = requireText(domain, "Dominio")
                .toLowerCase(Locale.ITALIAN)
                .replaceFirst("^@", "");
        if (!cleanDomain.contains(".") || cleanDomain.contains("/") || cleanDomain.contains(" ")) {
            throw new IllegalArgumentException("Dominio non valido");
        }
        return cleanDomain;
    }

    private String normalizeCode(String code) {
        return requireText(code, "Codice istituto").toUpperCase(Locale.ITALIAN);
    }

    private String normalizeStatus(String status, String defaultStatus, List<String> allowed) {
        String cleanStatus = status == null || status.isBlank()
                ? defaultStatus
                : status.trim().toUpperCase(Locale.ITALIAN);
        if (!allowed.contains(cleanStatus)) {
            throw new IllegalArgumentException("Stato non valido");
        }
        return cleanStatus;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " mancante");
        }
        return value.trim();
    }

    private String generateToken() {
        byte[] bytes = new byte[36];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
