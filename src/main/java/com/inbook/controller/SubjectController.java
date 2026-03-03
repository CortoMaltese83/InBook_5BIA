package com.inbook.controller;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.subjectService;
import com.inbook.repository.entity.Subject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SubjectController {

    private final subjectService subjectService;
    public SubjectController(subjectService subjectService) {this.subjectService = subjectService;}

    @PostMapping("/load-materia")
    public String loadmateria(@RequestParam(value = "classe")SchoolClass classe,
                              @RequestParam(value = "docente") AppUser docente,
                              @RequestParam(value = "nomeMateria")  String nomeMateria) {
        subjectService.loadMateria(classe,docente,nomeMateria,null,null);

        return "redirect:/admin/materias";
    }
}

