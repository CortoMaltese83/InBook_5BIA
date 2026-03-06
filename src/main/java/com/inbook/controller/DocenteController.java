package com.inbook.controller;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.DocenteService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class DocenteController {
    private DocenteService service;

    public DocenteController(DocenteService service) {
        this.service = service;
    }

    @GetMapping("/docente")
    public String DocenteHome() {
        return "homepageDocenti";
    }

    @GetMapping("/docente/classes")
    public String classManager(@AuthenticationPrincipal AppUser docente, Model model) {

        List<SchoolClass> classi;

        if (docente == null) {
            classi = List.of();
        } else {
            classi = service.getClassiByDocenteId(docente.getId());
        }

        model.addAttribute("classes", classi);

        return "classManagerDocenti";
    }
}