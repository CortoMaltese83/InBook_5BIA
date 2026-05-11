package com.inbook.controller;

import com.inbook.repository.entity.AppUser;
import com.inbook.service.InstitutionBookExportService;
import com.inbook.service.InstitutionAdminService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

@Controller
public class AdminInstitutionController {
    private final InstitutionAdminService service;
    private final InstitutionBookExportService bookExportService;

    public AdminInstitutionController(InstitutionAdminService service,
                                      InstitutionBookExportService bookExportService) {
        this.service = service;
        this.bookExportService = bookExportService;
    }

    @GetMapping("/admin/institutions")
    public String institutions(Model model, Principal principal) {
        AppUser user = service.requireLoggedUser(principal);
        if (!service.isAdmin(user)) {
            throw new AccessDeniedException("Forbidden");
        }

        model.addAttribute("username", user.getUsername());
        model.addAttribute("institutions", service.listInstitutions());
        model.addAttribute("domains", service.listDomains());
        model.addAttribute("teachers", service.listTeachers());
        model.addAttribute("invitations", service.listInvitations());
        model.addAttribute("auditEvents", service.latestAuditEvents());
        return "institutionManager";
    }

    @GetMapping("/admin/institutions/{id}/books.xlsx")
    public ResponseEntity<byte[]> exportActiveBooks(@PathVariable("id") Long id, Principal principal) {
        InstitutionBookExportService.ActiveBookExport export = bookExportService.exportActiveBooks(id, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .contentLength(export.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(export.content());
    }

    @PostMapping("/admin/institutions")
    public String createInstitution(@RequestParam("name") String name,
                                    @RequestParam("code") String code,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.createInstitution(name, code, principal), "Istituto creato.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/update")
    public String updateInstitution(@RequestParam("id") Long id,
                                    @RequestParam("name") String name,
                                    @RequestParam("code") String code,
                                    @RequestParam("status") String status,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.updateInstitution(id, name, code, status, principal), "Istituto aggiornato.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/domains")
    public String addDomain(@RequestParam("institutionId") Long institutionId,
                            @RequestParam("domain") String domain,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.addDomain(institutionId, domain, principal), "Dominio aggiunto.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/domains/toggle")
    public String toggleDomain(@RequestParam("id") Long id,
                               @RequestParam("active") boolean active,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.toggleDomain(id, active, principal), "Dominio aggiornato.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/invite")
    public String inviteTeacher(@RequestParam("institutionId") Long institutionId,
                                @RequestParam("email") String email,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.inviteTeacher(institutionId, email, principal), "Invito creato.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/invite/revoke")
    public String revokeInvite(@RequestParam("id") Long id,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.revokeInvitation(id, principal), "Invito revocato.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/teachers/approve")
    public String approveTeacher(@RequestParam("id") Long id,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.approveTeacher(id, principal), "Docente approvato.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/teachers/suspend")
    public String suspendTeacher(@RequestParam("id") Long id,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.suspendTeacher(id, principal), "Docente sospeso.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/teachers/reactivate")
    public String reactivateTeacher(@RequestParam("id") Long id,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.reactivateTeacher(id, principal), "Docente riattivato.");
        return "redirect:/admin/institutions";
    }

    @PostMapping("/admin/institutions/teachers/delete")
    public String deleteTeacher(@RequestParam("id") Long id,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        runAdminAction(redirectAttributes, () -> service.deleteTeacher(id, principal), "Docente cancellato definitivamente.");
        return "redirect:/admin/institutions";
    }

    private void runAdminAction(RedirectAttributes redirectAttributes, Runnable action, String successMessage) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
    }
}
