package com.inbook.controller;

import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.ClasseService;
import com.inbook.repository.entity.AppUser;
import java.security.Principal;
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

    private boolean isAdminUser(AppUser user) {
        if (user == null) return false;
        try {
            String rolesObj = user.getRoles();
            if (rolesObj == null) return false;

            String up = rolesObj.toUpperCase();
            return up.contains("ADMIN") || up.contains("TYPE_ADMIN");

        } catch (Exception ignore) {
        }
        return false;
    }

    @GetMapping("/admin/classes")
    public String classManager(Model model, Principal principal) {
        // Admin can CRUD, docente can only view
        AppUser user = service.requireLoggedUser(principal);
        boolean canManage = isAdminUser(user);
        model.addAttribute("canManageClasses", canManage);
        return "classManager";
    }

    @PostMapping("/classe")
    public String addClass(@RequestParam(value = "nome", required = false) String nome,
                           @RequestParam("anno") String anno,
                           @RequestParam("sezione") String sezione,
                           @RequestParam("stato") String stato,
                           Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        if (!isAdminUser(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        service.addClass(nome, anno, sezione, stato, null, null);
        return "redirect:/admin/classes";
    }

    @PostMapping("/classe/modify")
    public String editClass(@RequestParam("id") Long id,
                            @RequestParam(value = "nome", required = false) String nome,
                            @RequestParam("anno") String anno,
                            @RequestParam("sezione") String sezione,
                            @RequestParam("stato") String stato,
                            Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        if (!isAdminUser(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        service.modifyClass(id, nome, anno, sezione, stato, null);
        return "redirect:/admin/classes";
    }

    @PostMapping("/classe/delete")
    public String deleteClass(@RequestParam("id") Long id, Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        if (!isAdminUser(user)) {
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
    public List<Map<String, Object>> viewClass(Principal principal) {
        try {
            System.out.println("DEBUG: Fetching classes from service...");
            List<SchoolClass> classes = service.getAllClasses(principal);
            System.out.println("DEBUG: Found " + (classes != null ? classes.size() : "null") + " classes");
            
            List<Map<String, Object>> response = new ArrayList<>();
            if (classes != null) {
                for (SchoolClass c : classes) {
                    try {
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
                        map.put("stato", c.getStato());
                        map.put("created_at", c.getCreated_at());
                        map.put("updated_at", c.getUpdated_at());
                        response.add(map);
                    } catch (Exception rowEx) {
                        System.err.println("DEBUG: Error processing row ID " + c.getId() + ": " + rowEx.getMessage());
                    }
                }
            }
            return response;
        } catch (Exception e) {
            System.err.println("DEBUG FATAL in viewClass: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Errore recupero dati: " + e.getMessage());
        }
    }

}

