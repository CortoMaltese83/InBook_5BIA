package com.inbook.controller;

import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.ClasseService;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Institution;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
public class ClasseController{

    private final ClasseService service;

    public ClasseController(ClasseService service) {
        this.service = service;
    }

    @GetMapping("/admin/classes")
    public String classManager(Model model, Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        boolean canManage = service.canManageClasses(user);
        boolean canCreate = service.canCreateClass(user);
        model.addAttribute("canManageClasses", canManage);
        model.addAttribute("canCreateClasses", canCreate);
        model.addAttribute("institutions", service.listActiveInstitutions());
        model.addAttribute("username", user.getUsername());
        return "classManager";
    }

    @PostMapping("/classe")
    public String addClass(@RequestParam(value = "nome", required = false) String nome,
                           @RequestParam("anno") String anno,
                           @RequestParam("sezione") String sezione,
                           @RequestParam("stato") String stato,
                           @RequestParam(value = "institutionId", required = false) Long institutionId,
                           Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        if (!service.canCreateClass(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Per creare una classe devi essere associato a un istituto attivo.");
        }
        service.addClass(nome, anno, sezione, stato, null, null, user, institutionId);
        return "redirect:/admin/classes";
    }

    @PostMapping("/classe/modify")
    public String editClass(@RequestParam("id") Long id,
                            @RequestParam(value = "nome", required = false) String nome,
                            @RequestParam("anno") String anno,
                            @RequestParam("sezione") String sezione,
                            @RequestParam("stato") String stato,
                            @RequestParam(value = "institutionId", required = false) Long institutionId,
                            Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        if (!service.canManageClasses(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        service.modifyClass(id, nome, anno, sezione, stato, null, institutionId);
        return "redirect:/admin/classes";
    }

    @PostMapping("/classe/delete")
    public String deleteClass(@RequestParam("id") Long id, Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        if (!service.canManageClasses(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        try {
            service.deleteClass(id);
            return "redirect:/admin/classes";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/classe-data")
    @ResponseBody
    public Map<String, Object> viewClass(Principal principal,
                                         @RequestParam(name = "page", defaultValue = "0") int page,
                                         @RequestParam(name = "size", defaultValue = "25") int size,
                                         @RequestParam(name = "search", required = false) String search,
                                         @RequestParam(name = "stato", required = false) String stato) {
        try {
            System.out.println("DEBUG: Fetching classes from service...");
            Page<SchoolClass> classes = service.getClassesPage(principal, search, stato, page, size);
            System.out.println("DEBUG: Found " + classes.getTotalElements() + " classes");

            List<Map<String, Object>> items = new ArrayList<>();
            if (classes.hasContent()) {
                for (SchoolClass c : classes.getContent()) {
                    try {
                        items.add(toClassMap(c));
                    } catch (Exception rowEx) {
                        System.err.println("DEBUG: Error processing row ID " + c.getId() + ": " + rowEx.getMessage());
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("page", classes.getNumber());
            response.put("size", classes.getSize());
            response.put("totalItems", classes.getTotalElements());
            response.put("totalPages", classes.getTotalPages());
            return response;
        } catch (Exception e) {
            System.err.println("DEBUG FATAL in viewClass: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Errore recupero dati: " + e.getMessage());
        }
    }

    private Map<String, Object> toClassMap(SchoolClass c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("nome", c.getNome());

        String anno = c.getAnno();
        if (anno == null && c.getNome() != null && c.getSezione() != null) {
            // Fallback: Infer anno from nome (e.g., "5BIA" minus "BIA" = "5")
            anno = c.getNome().replace(c.getSezione(), "");
        }
        map.put("anno", anno);

        map.put("sezione", c.getSezione());
        Institution institution = service.effectiveInstitution(c);
        if (institution != null) {
            map.put("institution_id", institution.getId());
            map.put("institution_name", institution.getName());
            map.put("institution_code", institution.getCode());
        }
        map.put("stato", c.getStato());
        map.put("created_at", c.getCreated_at());
        map.put("updated_at", c.getUpdated_at());

        if (c.getDocente() != null) {
            map.put("docente_id", c.getDocente().getId());
            String name = c.getDocente().getName() != null ? c.getDocente().getName().trim() : "";
            String surname = c.getDocente().getSurname() != null ? c.getDocente().getSurname().trim() : "";
            String fullName = (name + " " + surname).trim();
            if (!fullName.isBlank()) {
                map.put("docente_nome", fullName);
            } else {
                map.put("docente_nome", c.getDocente().getUsername());
            }
        }

        return map;
    }

}
