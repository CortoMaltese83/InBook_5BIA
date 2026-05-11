package com.inbook.controller;

import com.inbook.repository.entity.AppUser;
import com.inbook.service.InstitutionAdminService;
import com.inbook.service.MimBookCatalogImportService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.Map;

@Controller
public class MimBookImportController {
    private final MimBookCatalogImportService importService;
    private final InstitutionAdminService institutionAdminService;

    public MimBookImportController(MimBookCatalogImportService importService,
                                   InstitutionAdminService institutionAdminService) {
        this.importService = importService;
        this.institutionAdminService = institutionAdminService;
    }

    @PostMapping("/admin/books/import/mim")
    @ResponseBody
    public Map<String, Object> importMimBooks(@RequestParam(name = "source", required = false) String source,
                                              Principal principal) {
        AppUser user = institutionAdminService.requireLoggedUser(principal);
        if (!institutionAdminService.isAdmin(user)) {
            throw new AccessDeniedException("Forbidden");
        }

        MimBookCatalogImportService.ImportSummary summary = source == null || source.isBlank()
                ? importService.importConfiguredSources()
                : importService.importSource(source);

        return Map.of(
                "sources", summary.sources(),
                "rowsRead", summary.rowsRead(),
                "saved", summary.saved(),
                "skipped", summary.skipped()
        );
    }
}
