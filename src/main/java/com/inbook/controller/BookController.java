package com.inbook.controller;

import com.inbook.dto.Book;
import com.inbook.service.BookService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BookController {
    private BookService service;
    public BookController(BookService service){
        this.service=service;
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
        model.addAttribute("books",service.);
        return "BookManager";
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
