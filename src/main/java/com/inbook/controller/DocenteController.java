package com.inbook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocenteController {
    @GetMapping("/docente")
    public String DocenteHome() {
        return "homepageDocenti";
    }

    @GetMapping("/docente/classes")
    public String classManager() {
        return "classManagerDocenti";
    }
}
