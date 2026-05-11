package com.inbook.service;

import com.inbook.repository.BookRepository;
import com.inbook.repository.InstitutionRepository;
import com.inbook.repository.SchoolClassRepository;
import com.inbook.repository.SubjectRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Institution;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ClasseService {
    private final SchoolClassRepository repo;
    private final InstitutionRepository institutionRepository;
    private final SubjectRepository subjectRepository;
    private final BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ClasseService(SchoolClassRepository repo,
                         InstitutionRepository institutionRepository,
                         SubjectRepository subjectRepository,
                         BookRepository bookRepository) {
        this.repo = repo;
        this.institutionRepository = institutionRepository;
        this.subjectRepository = subjectRepository;
        this.bookRepository = bookRepository;
    }

    public SchoolClass addClass(String nome, String anno, String sezione, String stato, Long created_at, Long update_at) {
        return addClass(nome, anno, sezione, stato, created_at, update_at, null, null);
    }

    public SchoolClass addClass(String nome, String anno, String sezione, String stato, Long created_at, Long update_at, AppUser creator) {
        return addClass(nome, anno, sezione, stato, created_at, update_at, creator, null);
    }

    public SchoolClass addClass(String nome, String anno, String sezione, String stato, Long created_at, Long update_at, AppUser creator, Long institutionId) {
        if (!canCreateClass(creator)) {
            throw new org.springframework.security.access.AccessDeniedException("Per creare una classe devi essere associato a un istituto attivo.");
        }

        Institution institution = resolveClassInstitution(creator, institutionId);
        SchoolClass c = new SchoolClass();
        c.setNome(anno + sezione);
        c.setAnno(anno);
        c.setSezione(sezione);
        c.setStato(stato);
        c.setInstitution(institution);
        if (!isAdminUser(creator)) {
            c.setDocente(creator);
        }
        c.setCreated_at(created_at != null ? created_at : System.currentTimeMillis());
        c.setUpdated_at(update_at != null ? update_at : System.currentTimeMillis());
        return repo.save(c);

    }

    public SchoolClass modifyClass(Long id, String nome, String anno, String sezione, String stato, Long update_at) {
        return modifyClass(id, nome, anno, sezione, stato, update_at, null);
    }

    public SchoolClass modifyClass(Long id, String nome, String anno, String sezione, String stato, Long update_at, Long institutionId) {
        SchoolClass c = repo.findById(id).orElseThrow(() -> new RuntimeException("classe non trovata"));
        c.setNome(anno + sezione);
        c.setAnno(anno);
        c.setSezione(sezione);
        c.setStato(stato);
        if (institutionId != null) {
            c.setInstitution(requireActiveInstitution(institutionId));
        }
        c.setUpdated_at(update_at != null ? update_at : System.currentTimeMillis());
        return repo.save(c);
    }

    @Transactional
    public void deleteClass(Long id){
        SchoolClass schoolClass = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("classe non trovata"));

        List<Subject> subjects = subjectRepository.findByClasse_Id(id);
        Set<String> candidateBookIsbns = new LinkedHashSet<>();
        for (Subject subject : subjects) {
            if (subject.getBook() != null && subject.getBook().getIsbn() != null) {
                candidateBookIsbns.add(subject.getBook().getIsbn());
            }
        }

        subjectRepository.deleteAll(subjects);
        subjectRepository.flush();

        repo.delete(schoolClass);
        repo.flush();

        deleteUnreferencedBooks(candidateBookIsbns);

    }

    private void deleteUnreferencedBooks(Set<String> candidateBookIsbns) {
        for (String isbn : candidateBookIsbns) {
            if (!subjectRepository.existsByBook_Isbn(isbn)) {
                bookRepository.deleteByIsbn(isbn);
            }
        }
    }
    public List<SchoolClass> getAllClasses() {
        return repo.findAll();
    }

    public List<Institution> listActiveInstitutions() {
        if (institutionRepository == null) {
            return List.of();
        }
        return institutionRepository.findAllByOrderByNameAsc().stream()
                .filter(institution -> "ACTIVE".equalsIgnoreCase(institution.getStatus()))
                .toList();
    }

    // --- Auth helpers (used by ClasseController) ---
    public AppUser requireLoggedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("Utente non autenticato");
        }
        String email = principal.getName();

        TypedQuery<AppUser> q = entityManager.createQuery(
                "select u from AppUser u where u.email = :email", AppUser.class);
        q.setParameter("email", email);

        List<AppUser> res = q.getResultList();
        if (res.isEmpty()) {
            throw new IllegalArgumentException("Utente non trovato: " + email);
        }
        return res.get(0);
    }

    public boolean canCreateClass(AppUser user) {
        return isAdminUser(user) || hasActiveInstitution(user);
    }

    public boolean canManageClasses(AppUser user) {
        return isAdminUser(user);
    }

    public Institution effectiveInstitution(SchoolClass schoolClass) {
        if (schoolClass == null) {
            return null;
        }
        if (schoolClass.getInstitution() != null) {
            return schoolClass.getInstitution();
        }
        AppUser docente = schoolClass.getDocente();
        return docente != null ? docente.getInstitution() : null;
    }

    private Institution resolveClassInstitution(AppUser creator, Long institutionId) {
        if (isAdminUser(creator)) {
            if (institutionId == null) {
                throw new IllegalArgumentException("Istituto classe mancante");
            }
            return requireActiveInstitution(institutionId);
        }
        Institution institution = creator != null ? creator.getInstitution() : null;
        if (institution == null || !"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
            throw new org.springframework.security.access.AccessDeniedException("Per creare una classe devi essere associato a un istituto attivo.");
        }
        return institution;
    }

    private Institution requireActiveInstitution(Long institutionId) {
        if (institutionRepository == null) {
            throw new IllegalStateException("Repository istituti non disponibile");
        }
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new IllegalArgumentException("Istituto non trovato"));
        if (!"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
            throw new IllegalArgumentException("Istituto non attivo");
        }
        return institution;
    }

    private boolean hasActiveInstitution(AppUser user) {
        if (user == null || user.getInstitution() == null) {
            return false;
        }
        String status = user.getInstitution().getStatus();
        return status == null || "ACTIVE".equalsIgnoreCase(status);
    }

    private boolean isAdminUser(AppUser user) {
        if (user == null) return false;
        try {
            String rolesObj = user.getRoles();
            if (rolesObj == null) return false;

            String up = rolesObj.toUpperCase();
            return up.contains("ADMIN") || up.contains("ROLE_ADMIN");

        } catch (Exception ignore) {
        }
        return false;
    }

    // Reflection helper to avoid coupling to a specific SchoolClass mapping
    private static Object tryInvoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ignore) {
            return null;
        }
    }

    public List<SchoolClass> getAllClasses(Principal principal) {
        return getAllClasses();
    }

    public Page<SchoolClass> getClassesPage(Principal principal, String search, String stato, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 5), 100);
        String cleanSearch = normalizeParam(search);
        String cleanStato = normalizeParam(stato);

        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(
                Sort.Order.asc("stato"),
                Sort.Order.asc("anno"),
                Sort.Order.asc("sezione"),
                Sort.Order.asc("nome")
        ));

        AppUser user = requireLoggedUser(principal);
        Long institutionId = null;
        if (!isAdminUser(user)) {
            Institution institution = user.getInstitution();
            if (institution == null || !"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
                return Page.empty(pageable);
            }
            institutionId = institution.getId();
        }

        return repo.searchClasses(cleanSearch, institutionId, cleanStato, pageable);
    }

    private String normalizeParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
