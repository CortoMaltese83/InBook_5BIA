package com.inbook.repository;

import com.inbook.repository.entity.Book;
import com.inbook.repository.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByIsbn(String isbn);
}
