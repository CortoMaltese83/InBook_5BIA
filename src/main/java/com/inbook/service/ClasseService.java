package com.inbook.service;

import com.inbook.repository.SchoolClassRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClasseService {
    private final SchoolClassRepository repo;

    @PersistenceContext
    private EntityManager entityManager;

    public ClasseService(SchoolClassRepository repo) {
        this.repo = repo;
    }

    public SchoolClass addClass(String nome, String anno, String sezione, String stato, Long created_at, Long update_at) {

        SchoolClass c = new SchoolClass();
        c.setNome(anno + sezione);
        c.setAnno(anno);
        c.setSezione(sezione);
        c.setStato(stato);
        c.setCreated_at(created_at != null ? created_at : System.currentTimeMillis());
        c.setUpdated_at(update_at != null ? update_at : System.currentTimeMillis());
        return repo.save(c);

    }

    public SchoolClass modifyClass(Long id, String nome, String anno, String sezione, String stato, Long update_at) {
        SchoolClass c = repo.findById(id).orElseThrow(() -> new RuntimeException("classe non trovata"));
        c.setNome(anno + sezione);
        c.setAnno(anno);
        c.setSezione(sezione);
        c.setStato(stato);
        c.setUpdated_at(update_at != null ? update_at : System.currentTimeMillis());
        return repo.save(c);
    }

    public void deleteClass(Long id){
        if (repo.existsById(id)){
            repo.deleteById(id);
        }
        else{
            throw new RuntimeException("classe non trovata");
        }

    }
    public List<SchoolClass> getAllClasses() {
        return repo.findAll();
    }

    // --- Auth helpers (used by ClasseController) ---
    public AppUser requireLoggedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("Utente non autenticato");
        }
        String username = principal.getName();

        TypedQuery<AppUser> q = entityManager.createQuery(
                "select u from AppUser u where u.username = :username", AppUser.class);
        q.setParameter("username", username);

        List<AppUser> res = q.getResultList();
        if (res.isEmpty()) {
            throw new IllegalArgumentException("Utente non trovato: " + username);
        }
        return res.get(0);
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

    /**
     * Overload used by /classe-data:
     * No association docente<->class: everyone sees all classes
     */
    public List<SchoolClass> getAllClasses(Principal principal) {
        // No association docente<->class: everyone sees all classes
        return getAllClasses();
    }
}



