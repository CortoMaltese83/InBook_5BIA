package com.inbook.service;


import com.inbook.repository.BookRepository;
import com.inbook.repository.entity.Book;
import com.inbook.repository.entity.Subject;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) { this.bookRepository = bookRepository; }

    public void addBook(String isbn, String autore, String titolo, int volume, String casaEditrice, double prezzo, boolean daAcquistare, boolean consigliato){

        Book book = new Book();

        book.setIsbn(isbn);
        book.setAutore(autore);
        book.setTitolo(titolo);
        book.setVolume(volume);
        book.setCasaEditrice(casaEditrice);
        book.setPrezzo(prezzo);
        book.setDaAcquistare(daAcquistare);
        book.setConsigliato(consigliato);

        bookRepository.save(book);
    }

    public Book modifyBook(String isbn, String autore, String titolo, int volume, String casaEditrice, double prezzo, boolean daAcquistare, boolean consigliato) {
        Book book = bookRepository.findByIsbn(isbn).orElseThrow(() -> new RuntimeException("libro non trovato"));

        book.setIsbn(isbn);
        book.setAutore(autore);
        book.setTitolo(titolo);
        book.setVolume(volume);
        book.setCasaEditrice(casaEditrice);
        book.setPrezzo(prezzo);
        book.setDaAcquistare(daAcquistare);
        book.setConsigliato(consigliato);

        return bookRepository.save(book);
    }

    public void deleteSubject(String isbn) {
        if (bookRepository.existsByIsbn(isbn)) {
            bookRepository.deleteByIsbn(isbn);
        }
        else{
            throw new RuntimeException("materia non trovata");
        }
    }

    /*public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }
     */

}
