package com.inbook.service;

import com.inbook.dto.Book;
import com.inbook.repository.BookRepository;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    private final BookRepository repo;

    public BookService(BookRepository repo) { this.repo = repo; }

    public BookRepository addBook(String isbn, String autore, String titolo, int volume, String casaEditrice, int prezzo, boolean daAcquistare, boolean consigliato){

        Book book = new Book();

        return repo.save(book);
    }
}
