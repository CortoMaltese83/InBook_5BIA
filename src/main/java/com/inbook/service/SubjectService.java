package com.inbook.service;

import com.inbook.repository.SubjectRepository;
import com.inbook.repository.BookRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Book;
import com.inbook.repository.entity.Institution;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.security.Principal;

@Service
public class SubjectService {
    private final SubjectRepository subjectRepository;
    private final BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SubjectService(SubjectRepository subjectRepository, BookRepository bookRepository) {
        this.subjectRepository = subjectRepository;
        this.bookRepository = bookRepository;
    }

    public Subject loadMateria(SchoolClass classe, AppUser docente, String nomeMateria, Long created_at, Long updated_at) {
        if (!canAccessClass(docente, classe)) {
            throw new AccessDeniedException("Non puoi creare materie per una classe di un altro istituto.");
        }
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
        if (!canModifySubject(docente, s)) {
            throw new AccessDeniedException("Puoi modificare solo le tue materie.");
        }
        if (!canAccessClass(docente, classe)) {
            throw new AccessDeniedException("Non puoi spostare la materia su una classe di un altro istituto.");
        }
        s.setClasse(classe);
        s.setNomeMateria(nomeMateria);
        s.setUpdated_at(updated_at != null ? updated_at : System.currentTimeMillis());
        return subjectRepository.save(s);
    }

    public void deleteSubject(Long id) {
        deleteSubject(id, null);
    }

    public void deleteSubject(Long id, AppUser actor) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("materia non trovata"));
        if (!canModifySubject(actor, subject)) {
            throw new AccessDeniedException("Puoi eliminare solo le tue materie.");
        }
        subjectRepository.delete(subject);
    }

    public List<Subject> getAllSubjects(Long classeId) {
        return subjectRepository.findByClasse_Id(classeId);
    }

    public Page<Subject> getSubjectsPage(Long classeId, String search, String bookStatus, int page, int size) {
        return getSubjectsPage(classeId, search, bookStatus, page, size, null);
    }

    public Page<Subject> getSubjectsPage(Long classeId, String search, String bookStatus, int page, int size, AppUser viewer) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 5), 100);
        String cleanSearch = normalizeParam(search);
        String cleanBookStatus = normalizeParam(bookStatus);

        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(
                Sort.Order.asc("nomeMateria"),
                Sort.Order.asc("id")
        ));

        Long institutionId = null;
        if (viewer != null && !isAdminUser(viewer)) {
            Institution institution = viewer.getInstitution();
            if (institution == null || !"ACTIVE".equalsIgnoreCase(institution.getStatus())) {
                return Page.empty(pageable);
            }
            institutionId = institution.getId();
        }

        if (classeId != null && viewer != null && !canAccessClass(viewer, requireClass(classeId))) {
            throw new AccessDeniedException("Non puoi visualizzare le materie di una classe di un altro istituto.");
        }

        return subjectRepository.searchSubjects(
                classeId,
                institutionId,
                cleanSearch,
                cleanBookStatus,
                pageable
        );
    }

    private String normalizeParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional
    public Subject associateBook(Long subjectId, String isbn, String autore, String titolo, int volume,
                                 String casaEditrice, double prezzo, boolean daAcquistare, boolean consigliato) {
        return associateBook(subjectId, isbn, autore, titolo, volume, casaEditrice, prezzo, daAcquistare, consigliato, null);
    }

    @Transactional
    public Subject associateBook(Long subjectId, String isbn, String autore, String titolo, int volume,
                                 String casaEditrice, double prezzo, boolean daAcquistare, boolean consigliato,
                                 AppUser actor) {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId mancante");
        }
        String cleanIsbn = requireText(isbn, "ISBN");
        String cleanAutore = requireText(autore, "Autore");
        String cleanTitolo = requireText(titolo, "Titolo del testo");
        String cleanCasaEditrice = requireText(casaEditrice, "Casa editrice");
        if (volume < 0) {
            throw new IllegalArgumentException("Volume non valido");
        }
        if (prezzo < 0) {
            throw new IllegalArgumentException("Prezzo non valido");
        }

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("materia non trovata"));
        if (!canModifySubject(actor, subject)) {
            throw new AccessDeniedException("Puoi associare libri solo alle tue materie.");
        }

        Book currentBook = subject.getBook();
        String currentIsbn = currentBook != null ? currentBook.getIsbn() : null;
        Book book = resolveBookForIsbn(currentBook, cleanIsbn);

        book.setIsbn(cleanIsbn);
        book.setAutore(cleanAutore);
        book.setTitolo(cleanTitolo);
        book.setVolume(volume);
        book.setCasaEditrice(cleanCasaEditrice);
        book.setPrezzo(prezzo);
        book.setDaAcquistare(daAcquistare);
        book.setConsigliato(consigliato);
        Book savedBook = bookRepository.save(book);

        subject.setBook(savedBook);
        subject.setUpdated_at(System.currentTimeMillis());
        Subject savedSubject = subjectRepository.save(subject);

        deleteUnreferencedOldBook(currentBook, currentIsbn, cleanIsbn);
        return savedSubject;
    }

    private Book resolveBookForIsbn(Book currentBook, String isbn) {
        if (currentBook != null && isbn.equals(currentBook.getIsbn())) {
            return currentBook;
        }

        return bookRepository.findByIsbn(isbn).orElseGet(Book::new);
    }

    private void deleteUnreferencedOldBook(Book oldBook, String oldIsbn, String newIsbn) {
        if (oldBook == null || oldIsbn == null || oldIsbn.equals(newIsbn)) {
            return;
        }
        entityManager.flush();
        if (!subjectRepository.existsByBook_Isbn(oldIsbn)) {
            bookRepository.delete(oldBook);
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " mancante");
        }
        return value.trim();
    }


    public SchoolClass requireClass(Long classeId) {
        if (classeId == null) throw new IllegalArgumentException("classeId mancante");
        SchoolClass cls = entityManager.find(SchoolClass.class, classeId);
        if (cls == null) throw new IllegalArgumentException("Classe non trovata: " + classeId);
        return cls;
    }

    public boolean canAccessClass(AppUser user, SchoolClass schoolClass) {
        if (isAdminUser(user)) {
            return true;
        }
        Institution userInstitution = user != null ? user.getInstitution() : null;
        Institution classInstitution = effectiveInstitution(schoolClass);
        return userInstitution != null
                && classInstitution != null
                && userInstitution.getId() != null
                && userInstitution.getId().equals(classInstitution.getId())
                && "ACTIVE".equalsIgnoreCase(userInstitution.getStatus());
    }

    public boolean canModifySubject(AppUser user, Subject subject) {
        if (isAdminUser(user)) {
            return true;
        }
        AppUser owner = subject != null ? subject.getDocente() : null;
        return user != null
                && owner != null
                && user.getId() != null
                && user.getId().equals(owner.getId());
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

    private boolean isAdminUser(AppUser user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        String roles = user.getRoles().toUpperCase();
        return roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
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
