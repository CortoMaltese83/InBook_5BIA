package com.inbook.service;

import com.inbook.repository.SubjectRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import org.springframework.stereotype.Service;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.security.Principal;

@Service
public class SubjectService {
    private final SubjectRepository subjectRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    public Subject loadMateria(SchoolClass classe, AppUser docente, String nomeMateria, Long created_at, Long updated_at) {
        Subject s= new Subject();
        s.setClasse(classe);
        s.setDocente(docente);
        s.setNomeMateria(nomeMateria);
        s.setCreated_at(created_at != null ? created_at : System.currentTimeMillis());
        s.setUpdated_at(updated_at != null ? updated_at : System.currentTimeMillis());
        return subjectRepository.save(s);
    }

    public Subject modifySubject(Long id,SchoolClass classe,AppUser docente ,String nomeMateria, Long updated_at) {
        Subject s= subjectRepository.findById(id).orElseThrow(() -> new RuntimeException("classe non trovata"));
        s.setClasse(classe);
        s.setDocente(docente);
        s.setNomeMateria(nomeMateria);
        s.setUpdated_at(updated_at != null ? updated_at : System.currentTimeMillis());
        return subjectRepository.save(s);
    }

    public void deleteSubject(Long id) {
        if (subjectRepository.existsById(id)) {
            subjectRepository.deleteById(id);
        }
        else{
            throw new RuntimeException("materia non trovata");
        }
    }

    public List<Subject> getAllSubjects(Long classeId) {
        return subjectRepository.findByClasse_Id(classeId);
    }


    public SchoolClass requireClass(Long classeId) {
        if (classeId == null) throw new IllegalArgumentException("classeId mancante");
        SchoolClass cls = entityManager.find(SchoolClass.class, classeId);
        if (cls == null) throw new IllegalArgumentException("Classe non trovata: " + classeId);
        return cls;
    }

    public AppUser requireLoggedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("Utente non autenticato");
        }
        String username = principal.getName();

        TypedQuery<AppUser> q = entityManager.createQuery(
                "select u from AppUser u where u.username = :username", AppUser.class);
        q.setParameter("username", username);

        List<AppUser> res = q.getResultList();
        if (res.isEmpty()) throw new IllegalArgumentException("Utente non trovato: " + username);
        return res.get(0);
    }
}
