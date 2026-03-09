package com.inbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/default")
    public String defaultAfterLogin(HttpServletRequest request) {
        return "redirect:/admin/classes";
    }
}