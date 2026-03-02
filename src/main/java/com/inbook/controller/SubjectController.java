package com.inbook.controller;

import com.inbook.service.SubjectService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SubjectController {

    private final SubjectService subjectService;
    public SubjectController(SubjectService subjectService) {this.subjectService = subjectService;}

    @GetMapping("/load-materia")
    public String loadmateria() {

        return "";
    }
}
