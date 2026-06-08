package com.inbook.controller;

import com.inbook.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {
    private static final String RESET_REQUEST_MESSAGE =
            "Se l'indirizzo e associato a un docente attivo, riceverai una mail con il link di reset.";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/password-reset/request")
    public String showRequestForm() {
        return "passwordResetRequest";
    }

    @PostMapping("/password-reset/request")
    public String requestReset(@RequestParam("email") String email,
                               RedirectAttributes redirectAttributes) {
        passwordResetService.requestTeacherPasswordReset(email);
        redirectAttributes.addFlashAttribute("successMessage", RESET_REQUEST_MESSAGE);
        return "redirect:/password-reset/request";
    }

    @GetMapping("/password-reset/reset")
    public String showResetForm(@RequestParam(value = "token", required = false) String token, Model model) {
        if (!passwordResetService.isResetTokenValid(token)) {
            model.addAttribute("title", "Link non valido");
            model.addAttribute("message", "Il link di reset non e valido o e scaduto. Puoi richiederne uno nuovo.");
            return "authStatus";
        }

        model.addAttribute("token", token);
        return "passwordResetForm";
    }

    @PostMapping("/password-reset/reset")
    public String resetPassword(@RequestParam("token") String token,
                                @RequestParam("password") String password,
                                @RequestParam("confirmPassword") String confirmPassword,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            passwordResetService.resetPassword(token, password, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Password aggiornata. Puoi accedere con la nuova password.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("token", token);
            model.addAttribute("errorMessage", e.getMessage());
            return "passwordResetForm";
        }
    }
}
