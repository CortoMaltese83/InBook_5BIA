package com.inbook.controller;

import com.inbook.repository.entity.SchoolClass;
import com.inbook.service.ClasseService;
import com.inbook.service.DbUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;


@Controller
public class ClasseController{
    private final ClasseService service;

    public ClasseController(ClasseService service) {
        this.service = service;
    }
    @PostMapping("/classe")
    public String RegisterClass(SchoolClass s) {
        try{
            service.AddClass(
                    s.getNome(),
                    s.getAnnoScolastico(),
                    s.getSezione(),
                    s.getStato(),
                    s.getCreated_at(),
                    s.getUpdated_at()
            );

            return "redirect:/classManager";
    } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @PostMapping("/classe/modify")
    public String modifyClass(SchoolClass s) {
        try{
            service.modifyClass(
                    s.getId(),
                    s.getNome(),
                    s.getAnnoScolastico(),
                    s.getSezione(),
                    s.getStato(),
                    s.getUpdated_at()
            );

            return "redirect:/classManager";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @PostMapping("/classe/delete")
    public String deleteClass(SchoolClass s) {
        try{
            service.deleteClass(s.getId());

            return "redirect:/classManager";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @PostMapping("/classe/view")
    public String viewClass(SchoolClass s) {
        try{
            service.getAllClasses();

            return "redirect:/classManager";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}