package com.inbook.controller;

import com.inbook.dto.Book;
import com.inbook.service.BookService;
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
    public BookController(BookService service){
        this.service=service;
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

    @PostMapping("/book/edit")
    public String editBook(Book book){

        service.modifyBook(book.getIsbn(),book.getAutore(),book.getTitolo()
                ,book.getVolume(),book.getCasaEditrice(),book.getPrezzo()
                ,book.isDaAcquistare(),book.isConsigliato());
        return "redirect:/book/view";
    }
    @PostMapping("/book/delete")
    public String deleteBook(@RequestParam("isbn")String isbn){
        try {
            service.deleteBook(isbn);
            return "redirect:/book/view";
        }catch (Exception e){
            throw new  RuntimeException(e);
        }
    }


}
