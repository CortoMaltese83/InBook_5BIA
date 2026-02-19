package com.inbook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    @GetMapping("/admin")
    public String adminHome() {
        return "homepage";
    }

    @GetMapping("/admin/classes")
    public String classManager() {
        return "classManager";
    }
}
