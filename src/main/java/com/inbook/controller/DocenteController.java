package com.inbook.controller;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.DocenteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class DocenteController {
    private DocenteService service;
    public DocenteController(DocenteService service){
        this.service=service;
    }
    @GetMapping("/docente")
    public String DocenteHome() {
        return "homepageDocenti";
    }

    // Spring Security inietta automaticamente l'utente loggato
    @GetMapping("/api/docente/classes")
    @ResponseBody
    public ResponseEntity<List<SchoolClass>> getClassiDocente
            (@AuthenticationPrincipal AppUser docente) {
        Long docenteId = docente.getId();
        List<SchoolClass> classi = service.getClassiByDocenteId(docenteId);
        return ResponseEntity.ok(classi);
    }

    @GetMapping("/docente/subjects")
    public String subjectsManager() {return "subjectManager";}
}
