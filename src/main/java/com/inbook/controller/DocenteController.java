package com.inbook.controller;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.DocenteService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
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

    @GetMapping("/api/docente/classes")
    @ResponseBody
    public ResponseEntity<List<SchoolClass>> getClassiDocente(
            @AuthenticationPrincipal AppUser docente) {
        if (docente == null) {
            return ResponseEntity.ok(List.of());
        }

        Long docenteId = docente.getId();
        List<SchoolClass> classi = service.getClassiByDocenteId(docenteId);
        return ResponseEntity.ok(classi);
    }
}