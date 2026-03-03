package com.inbook.controller;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.subjectService;
import com.inbook.repository.entity.Subject;
import com.inbook.repository.entity.Subject;
import com.inbook.service.SubjectService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SubjectController {

    private final subjectService subjectService;
    public SubjectController(subjectService subjectService) {this.subjectService = subjectService;}


    @GetMapping("/docente/subjects")
    public String subjectManager() {
        return "subjectManager";
    }

    @GetMapping("/materia-data")
    @ResponseBody
    public List<Map<String, Object>> viewSubjects(@RequestParam(name = "classeId", required = false) Long classeId) {
        try {
            System.out.println("DEBUG: Fetching subjects from service...");
            List<Subject> subjects = subjectService.getAllSubjects();
            System.out.println("DEBUG: Found " + (subjects != null ? subjects.size() : "null") + " subjects");
            List<Map<String, Object>> response = new ArrayList<>();
            if (subjects != null) {
                for (Subject s : subjects) {
                    try {
                        // Optional filter by class
                        if (classeId != null) {
                            if (s.getClasse() == null || s.getClasse().getId() == null) continue;
                            if (!classeId.equals(s.getClasse().getId())) continue;
                        }

                        Map<String, Object> map = new HashMap<>();

                        // PK
                        map.put("id", s.getId());

                        // FK: classe_id
                        map.put("classe_id", (s.getClasse() != null ? s.getClasse().getId() : null));

                        // FK: docente_id (+ optional teacher display)
                        map.put("docente_id", (s.getDocente() != null ? s.getDocente().getId() : null));
                        if (s.getDocente() != null) {
                            // Optional: if AppUser has getName()/getSurname(), expose a readable label
                            try {
                                String name = null;
                                String surname = null;
                                // These methods exist in your user entity in many codebases; keep inside try to avoid compilation issues
                                name = s.getDocente().getName();
                                surname = s.getDocente().getSurname();
                                String fullName = (name != null ? name : "").trim();
                                if (surname != null && !surname.isBlank()) {
                                    fullName = (fullName.isEmpty() ? surname.trim() : (fullName + " " + surname.trim()));
                                }
                                if (!fullName.isBlank()) {
                                    map.put("docente_nome", fullName);
                                }
                            } catch (Exception ignore) {
                                // If AppUser doesn't expose name/surname methods, we just omit docente_nome
                            }
                        }

                        // Subject fields
                        map.put("nome_materia", s.getNomeMateria());

                        // Timestamps (stored as Long in DB)
                        map.put("created_at", s.getCreated_at());
                        map.put("updated_at", s.getUpdated_at());

                        response.add(map);
                    } catch (Exception rowEx) {
                        System.err.println("DEBUG: Error processing subject ID " + (s != null ? s.getId() : "null") + ": " + rowEx.getMessage());
                    }
                }
            }

            return response;
        } catch (Exception e) {
            System.err.println("DEBUG FATAL in viewSubjects: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Errore recupero dati: " + e.getMessage());
        }
}

