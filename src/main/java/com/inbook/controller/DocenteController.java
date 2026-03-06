package com.inbook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocenteController {
    @GetMapping("/docente")
    public String DocenteHome() {
        return "homepageDocenti";
    }

    @GetMapping("/docente/view")
    public String DocenteView() {

        return "classManagerDocenti";
    }
    @GetMapping("/docente/classes")
    public String classManager() {
        return "classManagerDocenti";
    }

    @GetMapping("/docente/subjects")
    public String subjectsManager() {return "subjectManager";}
}
