package com.inbook.service;


import com.inbook.repository.BookRepository;
import com.inbook.repository.entity.Book;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public Book modifyBook(Long id, String isbn, String autore, String titolo, int volume, String casaEditrice, double prezzo, boolean daAcquistare, boolean consigliato) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("libro non trovato"));

        // ISBN resta un campo (può essere aggiornato), la chiave è id
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

    public void deleteBook(Long id) {
        if (bookRepository.existsById(id)) {
            bookRepository.deleteById(id);
        } else {
            throw new RuntimeException("libro non trovato");
        }
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }


}
