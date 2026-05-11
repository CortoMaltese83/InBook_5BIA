package com.inbook.controller;

import com.inbook.dto.Book;
import com.inbook.dto.BookLookupResult;
import com.inbook.service.BookLookupService;
import com.inbook.service.BookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class BookController {
    private BookService service;
    private final BookLookupService bookLookupService;

    public BookController(BookService service, BookLookupService bookLookupService){
        this.service=service;
        this.bookLookupService = bookLookupService;
    }

    private static Object tryInvoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
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

    @PostMapping("/book/insert")
    public String insertBook(Book book){
        service.addBook(book.getIsbn(),book.getAutore(),book.getTitolo()
                ,book.getVolume(),book.getCasaEditrice(),book.getPrezzo()
                ,book.isDaAcquistare(),book.isConsigliato());
        return "redirect:/book/view";
    }

    @GetMapping("/book/view")
    public String viewBooks(Model model){
        model.addAttribute("books",service.getAllBooks());
        return "BookManager";
    }

    @GetMapping("/book-data")
    @ResponseBody
    public List<Map<String, Object>> viewBooksData(@RequestParam(name = "subjectId", required = false) Long subjectId) {
        try {
            System.out.println("DEBUG: Fetching books from service...");
            List<com.inbook.repository.entity.Book> books = service.getAllBooks();
            System.out.println("DEBUG: Found " + (books != null ? books.size() : "null") + " books");

            List<Map<String, Object>> response = new ArrayList<>();
            if (books != null) {
                for (com.inbook.repository.entity.Book b : books) {
                    try {
                        // Optional filter by subject_id
                        if (subjectId != null) {
                            Long bookSubjectId = null;

                            // 1) try direct getters on DTO
                            bookSubjectId = asLong(tryInvoke(b, "getSubjectId"));
                            if (bookSubjectId == null) bookSubjectId = asLong(tryInvoke(b, "getSubject_id"));

                            // 2) try nested subject relation: getSubject().getId()
                            if (bookSubjectId == null) {
                                Object subj = tryInvoke(b, "getSubject");
                                if (subj != null) bookSubjectId = asLong(tryInvoke(subj, "getId"));
                            }

                            if (bookSubjectId == null || !subjectId.equals(bookSubjectId)) continue;
                        }

                        Map<String, Object> map = new HashMap<>();

                        map.put("id", b.getId());

                        map.put("isbn", b.getIsbn());
                        map.put("autore", b.getAutore());
                        map.put("titolo", b.getTitolo());
                        map.put("volume", b.getVolume());
                        map.put("casa_editrice", b.getCasaEditrice());
                        map.put("prezzo", b.getPrezzo());
                        map.put("da_acquistare", b.isDaAcquistare());
                        map.put("consigliato", b.isConsigliato());

                        // Expose subject_id if available
                        Long outSubjectId = asLong(tryInvoke(b, "getSubjectId"));
                        if (outSubjectId == null) outSubjectId = asLong(tryInvoke(b, "getSubject_id"));
                        if (outSubjectId == null) {
                            Object subj = tryInvoke(b, "getSubject");
                            if (subj != null) outSubjectId = asLong(tryInvoke(subj, "getId"));
                        }
                        if (outSubjectId != null) map.put("subject_id", outSubjectId);

                        response.add(map);
                    } catch (Exception rowEx) {
                        System.err.println("DEBUG: Error processing book ISBN " + (b != null ? b.getIsbn() : "null") + ": " + rowEx.getMessage());
                    }
                }
            }

            return response;
        } catch (Exception e) {
            System.err.println("DEBUG FATAL in viewBooksData: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Errore recupero dati: " + e.getMessage());
        }
    }

    @GetMapping("/book/lookup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lookupBook(@RequestParam("isbn") String isbn) {
        try {
            BookLookupResult result = bookLookupService.lookupByIsbn(isbn);
            Map<String, Object> response = new HashMap<>();
            response.put("isbn", result.getIsbn());
            response.put("autore", result.getAutore());
            response.put("titolo", result.getTitolo());
            response.put("volume", result.getVolume());
            response.put("casaEditrice", result.getCasaEditrice());
            response.put("prezzo", result.getPrezzo());
            response.put("source", result.getSource());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (BookLookupService.BookNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (BookLookupService.BookLookupUnavailableException e) {
            return error(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/book/edit")
    public String editBook(Book book){
        // Ora l'edit usa book_id (id) come chiave.
        Long id = asLong(tryInvoke(book, "getId"));
        if (id == null) {
            throw new RuntimeException("book_id (id) mancante per la modifica");
        }

        service.modifyBook(
                id,
                book.getIsbn(),
                book.getAutore(),
                book.getTitolo(),
                book.getVolume(),
                book.getCasaEditrice(),
                book.getPrezzo(),
                book.isDaAcquistare(),
                book.isConsigliato()
        );
        return "redirect:/book/view";
    }
    @PostMapping("/book/delete")
    public String deleteBook(@RequestParam("id") Long id){
        try {
            service.deleteBook(id);
            return "redirect:/book/view";
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }


}
