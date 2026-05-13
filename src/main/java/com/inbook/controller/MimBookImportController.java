package com.inbook.controller;

import com.inbook.repository.BookImportRunItemRepository;
import com.inbook.repository.BookImportRunRepository;
import com.inbook.repository.BookImportRunSourceRepository;
import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookLookupCache;
import com.inbook.service.BookIsbnFallbackBatchService;
import com.inbook.service.InstitutionAdminService;
import com.inbook.service.MimBookCatalogImportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class MimBookImportController {
    private final MimBookCatalogImportService importService;
    private final BookIsbnFallbackBatchService fallbackBatchService;
    private final InstitutionAdminService institutionAdminService;
    private final BookImportRunRepository runRepository;
    private final BookImportRunSourceRepository sourceRepository;
    private final BookImportRunItemRepository itemRepository;
    private final BookLookupCacheRepository cacheRepository;

    public MimBookImportController(MimBookCatalogImportService importService,
                                   BookIsbnFallbackBatchService fallbackBatchService,
                                   InstitutionAdminService institutionAdminService,
                                   BookImportRunRepository runRepository,
                                   BookImportRunSourceRepository sourceRepository,
                                   BookImportRunItemRepository itemRepository,
                                   BookLookupCacheRepository cacheRepository) {
        this.importService = importService;
        this.fallbackBatchService = fallbackBatchService;
        this.institutionAdminService = institutionAdminService;
        this.runRepository = runRepository;
        this.sourceRepository = sourceRepository;
        this.itemRepository = itemRepository;
        this.cacheRepository = cacheRepository;
    }

    @GetMapping("/admin/books/import")
    public String importBooks(@RequestParam(name = "cacheSearch", required = false) String cacheSearch,
                              Model model,
                              Principal principal) {
        AppUser user = requireAdmin(principal);
        List<BookImportRun> latestRuns = runRepository.findLatest(PageRequest.of(0, 20));
        String cleanCacheSearch = cacheSearch == null ? "" : cacheSearch.trim();
        List<BookLookupCache> cacheSearchResults = cleanCacheSearch.isBlank()
                ? List.of()
                : cacheRepository.searchCache(cleanCacheSearch, normalizeDigits(cleanCacheSearch), PageRequest.of(0, 25));

        model.addAttribute("username", user.getEmail());
        model.addAttribute("configuredSources", importService.getConfiguredSources());
        model.addAttribute("cacheSearch", cleanCacheSearch);
        model.addAttribute("cacheSearchExecuted", !cleanCacheSearch.isBlank());
        model.addAttribute("cacheSearchResults", cacheSearchResults);
        model.addAttribute("latestRuns", latestRuns);
        return "bookImport";
    }

    @GetMapping("/admin/books/import/runs/{id}")
    public String importRunDetail(@PathVariable("id") Long id,
                                  Model model,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        AppUser user = requireAdmin(principal);
        BookImportRun run = runRepository.findById(id).orElse(null);
        if (run == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Run non trovato.");
            return "redirect:/admin/books/import";
        }

        model.addAttribute("username", user.getEmail());
        model.addAttribute("run", run);
        model.addAttribute("runSources", sourceRepository.findByRunOrderByIdAsc(run));
        model.addAttribute("runItems", itemRepository.findByRunOrderByIdAsc(run));
        return "bookImportRunDetail";
    }

    @PostMapping("/admin/books/import/mim/run")
    public String runMimImport(Principal principal, RedirectAttributes redirectAttributes) {
        AppUser user = requireAdmin(principal);
        try {
            BookImportRun run = importService.importConfiguredSourcesWithRun(user);
            redirectAttributes.addFlashAttribute("successMessage", "Import Open Data MIM completato: " + run.getStatus() + ".");
            return "redirect:/admin/books/import/runs/" + run.getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/books/import";
        }
    }

    @PostMapping("/admin/books/import/fallback/run")
    public String runFallbackBatch(Principal principal, RedirectAttributes redirectAttributes) {
        AppUser user = requireAdmin(principal);
        BookImportRun run = fallbackBatchService.runFallbackBatch(user);
        if ("FAILED".equals(run.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Batch fallback fallito: " + run.getErrorMessage());
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Batch fallback completato: " + run.getStatus() + ".");
        }
        return "redirect:/admin/books/import/runs/" + run.getId();
    }

    @PostMapping("/admin/books/import/mim")
    @ResponseBody
    public Map<String, Object> importMimBooks(@RequestParam(name = "source", required = false) String source,
                                              Principal principal) {
        AppUser user = requireAdmin(principal);

        MimBookCatalogImportService.ImportSummary summary = source == null || source.isBlank()
                ? runSummary(importService.importConfiguredSourcesWithRun(user))
                : importService.importSource(source);

        return Map.of(
                "sources", summary.sources(),
                "rowsRead", summary.rowsRead(),
                "saved", summary.saved(),
                "skipped", summary.skipped()
        );
    }

    private MimBookCatalogImportService.ImportSummary runSummary(BookImportRun run) {
        return new MimBookCatalogImportService.ImportSummary(
                run.getSourceCount(),
                run.getRowsRead(),
                run.getSaved(),
                run.getSkipped()
        );
    }

    private AppUser requireAdmin(Principal principal) {
        AppUser user = institutionAdminService.requireLoggedUser(principal);
        if (!institutionAdminService.isAdmin(user)) {
            throw new AccessDeniedException("Forbidden");
        }
        return user;
    }

    private String normalizeDigits(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }
}
