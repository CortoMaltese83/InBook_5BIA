package com.inbook.service;

import com.inbook.repository.SubjectRepository;
import com.inbook.repository.BookRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Book;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
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

    @Transactional
    public Subject associateBook(Long subjectId, String isbn, String autore, String titolo, int volume,
                                 String casaEditrice, double prezzo, boolean daAcquistare, boolean consigliato) {
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
