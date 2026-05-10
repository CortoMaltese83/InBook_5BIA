package com.inbook.controller;

import com.inbook.dto.Docente;
import com.inbook.repository.entity.TeacherInvitation;
import com.inbook.service.InstitutionAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class RegisterController {
    private final InstitutionAdminService institutionAdminService;

    public RegisterController(InstitutionAdminService institutionAdminService) {
        this.institutionAdminService = institutionAdminService;
    }

    @PostMapping("/auth/register")
    public String Register(Docente d,
                           @RequestParam(name = "inviteToken", required = false) String inviteToken,
                           RedirectAttributes redirectAttributes) {
        try {
            institutionAdminService.registerTeacher(
                    d.getEmail(),
                    d.getPassword(),
                    d.getUsername(),
                    d.getName(),
                    d.getSurname(),
                    inviteToken
            );

            redirectAttributes.addFlashAttribute("successMessage", "Registrazione ricevuta. Controlla la tua email per verificare l'account.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            if (inviteToken != null && !inviteToken.isBlank()) {
                redirectAttributes.addAttribute("inviteToken", inviteToken);
            }
            return "redirect:/signin";
        }
    }

    @GetMapping("/signin")
    public String signin(@RequestParam(name = "inviteToken", required = false) String inviteToken, Model model) {
        if (inviteToken != null && !inviteToken.isBlank()) {
            Optional<TeacherInvitation> invitation = institutionAdminService.findInvitationByToken(inviteToken);
            invitation.ifPresent(value -> {
                model.addAttribute("inviteToken", value.getToken());
                model.addAttribute("inviteEmail", value.getEmail());
                model.addAttribute("inviteInstitution", value.getInstitution().getName());
            });
            if (invitation.isEmpty()) {
                model.addAttribute("errorMessage", "Invito non valido o scaduto.");
            }
        }
        return "signin";
    }

    @GetMapping("/auth/verify")
    public String verifyEmail(@RequestParam("token") String token, Model model) {
        try {
            var user = institutionAdminService.verifyEmail(token);
            if (user.isEnabled()) {
                model.addAttribute("title", "Account verificato");
                model.addAttribute("message", "Il tuo account docente e attivo. Ora puoi accedere.");
            } else {
                model.addAttribute("title", "Email verificata");
                model.addAttribute("message", "La tua email e verificata. L'account resta in attesa di approvazione da parte dell'amministratore.");
            }
        } catch (RuntimeException e) {
            model.addAttribute("title", "Verifica non riuscita");
            model.addAttribute("message", e.getMessage());
        }
        return "authStatus";
    }

    @GetMapping("/invite/accept")
    public String acceptInvite(@RequestParam("token") String token, RedirectAttributes redirectAttributes) {
        return institutionAdminService.findInvitationByToken(token)
                .map(invitation -> "redirect:/signin?inviteToken=" + invitation.getToken())
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Invito non valido o scaduto.");
                    return "redirect:/signin";
                });
    }
}
