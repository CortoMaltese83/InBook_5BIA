package com.inbook.controller;

import com.inbook.service.DbUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RegisterController {
    private DbUserDetailsService service;
    private PasswordEncoder passwordEncoder;
    public RegisterController(DbUserDetailsService service, PasswordEncoder passwordEncoder) {
        this.service = service;
        this.passwordEncoder = passwordEncoder;

    }
 // c
    @PostMapping("/auth/register")
  //  @ResponseBody
    public String Register(Docente d){
        try{
            service.RegisterUser(
                    d.getEmail(),
                    passwordEncoder.encode(d.getPassword()),
                    d.getUsername(),
                    d.getName(),
                    d.getSurname(),
                    "",
                    true
            );

            return "redirect:/login";
        }catch (RuntimeException e) {
        return "redirect:/signin";
    }
    }


    @GetMapping("/signin")
    public String signin() {
        return "signin";
    }
}
