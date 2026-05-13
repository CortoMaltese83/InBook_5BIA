package com.inbook.controller;

import com.inbook.repository.BookImportRunItemRepository;
import com.inbook.repository.BookImportRunErrorGroupRepository;
import com.inbook.repository.BookImportRunRepository;
import com.inbook.repository.BookImportRunSourceRepository;
import com.inbook.repository.BookLookupCacheRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunErrorGroup;
import com.inbook.repository.entity.BookImportRunItem;
import com.inbook.repository.entity.BookLookupCache;
import com.inbook.service.BookIsbnFallbackBatchService;
import com.inbook.service.InstitutionAdminService;
import com.inbook.service.MimBookCatalogImportService;
import org.springframework.core.task.TaskRejectedException;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
public class MimBookImportController {
    private final MimBookCatalogImportService importService;
    private final BookIsbnFallbackBatchService fallbackBatchService;
    private final InstitutionAdminService institutionAdminService;
    private final BookImportRunRepository runRepository;
    private final BookImportRunSourceRepository sourceRepository;
    private final BookImportRunItemRepository itemRepository;
    private final BookImportRunErrorGroupRepository errorGroupRepository;
    private final BookLookupCacheRepository cacheRepository;

    public MimBookImportController(MimBookCatalogImportService importService,
                                   BookIsbnFallbackBatchService fallbackBatchService,
                                   InstitutionAdminService institutionAdminService,
                                   BookImportRunRepository runRepository,
                                   BookImportRunSourceRepository sourceRepository,
                                   BookImportRunItemRepository itemRepository,
                                   BookImportRunErrorGroupRepository errorGroupRepository,
                                   BookLookupCacheRepository cacheRepository) {
        this.importService = importService;
        this.fallbackBatchService = fallbackBatchService;
        this.institutionAdminService = institutionAdminService;
        this.runRepository = runRepository;
        this.sourceRepository = sourceRepository;
        this.itemRepository = itemRepository;
        this.errorGroupRepository = errorGroupRepository;
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
        model.addAttribute("discardedGroups", toErrorGroupViews(id, run));
        return "bookImportRunDetail";
    }

    @GetMapping("/admin/books/import/runs/{id}/discarded/{key}")
    public String importRunDiscardedDetail(@PathVariable("id") Long id,
                                           @PathVariable("key") String key,
                                           Model model,
                                           Principal principal,
                                           RedirectAttributes redirectAttributes) {
        AppUser user = requireAdmin(principal);
        BookImportRun run = runRepository.findById(id).orElse(null);
        if (run == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Run non trovato.");
            return "redirect:/admin/books/import";
        }

        ErrorGroupView selectedGroup = toErrorGroupViews(id, run).stream()
                .filter(group -> group.key().equals(key))
                .findFirst()
                .orElse(null);
        if (selectedGroup == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Gruppo di errore non trovato.");
            return "redirect:/admin/books/import/runs/" + id;
        }

        List<BookImportRunItem> discardedItems = itemRepository.findByDiscardedGroup(
                run,
                selectedGroup.status(),
                selectedGroup.fallbackStep(),
                selectedGroup.reason()
        );

        model.addAttribute("username", user.getEmail());
        model.addAttribute("run", run);
        model.addAttribute("group", selectedGroup);
        model.addAttribute("discardedItems", discardedItems);
        return "bookImportRunDiscarded";
    }

    @PostMapping("/admin/books/import/runs/{id}/interrupt")
    public String interruptRun(@PathVariable("id") Long id,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        requireAdmin(principal);
        BookImportRun run = runRepository.findById(id).orElse(null);
        if (run == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Run non trovato.");
            return "redirect:/admin/books/import";
        }
        if (!canInterrupt(run)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Il run #" + id + " non e in stato interrompibile.");
            return "redirect:/admin/books/import/runs/" + id;
        }

        run.setStatus("INTERRUPTED");
        run.setFinished_at(System.currentTimeMillis());
        run.setErrorMessage("Run interrotto manualmente da amministratore.");
        runRepository.save(run);
        redirectAttributes.addFlashAttribute("successMessage", "Run #" + id + " marcato come INTERRUPTED.");
        return "redirect:/admin/books/import/runs/" + id;
    }

    private boolean canInterrupt(BookImportRun run) {
        if (run == null || run.getStatus() == null) {
            return false;
        }
        String status = run.getStatus().toUpperCase();
        return "RUNNING".equals(status) || "PENDING".equals(status);
    }

    @PostMapping("/admin/books/import/mim/run")
    public String runMimImport(Principal principal, RedirectAttributes redirectAttributes) {
        AppUser user = requireAdmin(principal);
        BookImportRun run = null;
        try {
            run = importService.startConfiguredSourcesRun(user);
            importService.importConfiguredSourcesInBackground(run.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Import Open Data MIM avviato in background.");
            return "redirect:/admin/books/import/runs/" + run.getId();
        } catch (MimBookCatalogImportService.ActiveImportRunException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Esiste gia un import Open Data MIM attivo: run #" + e.getRunId() + ".");
            return "redirect:/admin/books/import/runs/" + e.getRunId();
        } catch (TaskRejectedException e) {
            if (run != null) {
                importService.markRunFailed(run.getId(), "Executor import non disponibile: " + e.getMessage());
                redirectAttributes.addFlashAttribute("errorMessage", "Import Open Data MIM non avviato: executor occupato.");
                return "redirect:/admin/books/import/runs/" + run.getId();
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Import Open Data MIM non avviato: executor occupato.");
            return "redirect:/admin/books/import";
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

        if (source == null || source.isBlank()) {
            BookImportRun run = null;
            try {
                run = importService.startConfiguredSourcesRun(user);
                importService.importConfiguredSourcesInBackground(run.getId());
                return Map.of(
                        "runId", run.getId(),
                        "status", run.getStatus(),
                        "redirect", "/admin/books/import/runs/" + run.getId()
                );
            } catch (MimBookCatalogImportService.ActiveImportRunException e) {
                return Map.of(
                        "runId", e.getRunId(),
                        "status", "ALREADY_RUNNING",
                        "redirect", "/admin/books/import/runs/" + e.getRunId()
                );
            } catch (TaskRejectedException e) {
                if (run != null) {
                    importService.markRunFailed(run.getId(), "Executor import non disponibile: " + e.getMessage());
                    return Map.of(
                            "runId", run.getId(),
                            "status", "FAILED",
                            "redirect", "/admin/books/import/runs/" + run.getId()
                    );
                }
                return Map.of("status", "FAILED");
            }
        }

        MimBookCatalogImportService.ImportSummary summary = importService.importSource(source);

        return Map.of(
                "sources", summary.sources(),
                "rowsRead", summary.rowsRead(),
                "saved", summary.saved(),
                "skipped", summary.skipped()
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

    private List<ErrorGroupView> toErrorGroupViews(Long runId, BookImportRun run) {
        List<BookImportRunErrorGroup> persistedGroups = errorGroupRepository.findByRunOrderByItemCountDescFallbackStepAscReasonAsc(run);
        if (!persistedGroups.isEmpty()) {
            return persistedGroups.stream()
                    .map(group -> new ErrorGroupView(
                            errorGroupKey(group.getStatus(), group.getFallbackStep(), group.getReason()),
                            runId,
                            group.getStatus(),
                            group.getFallbackStep(),
                            group.getReason(),
                            group.getItemCount()
                    ))
                    .toList();
        }

        return itemRepository.findDiscardedGroups(run).stream()
                .map(group -> new ErrorGroupView(
                        errorGroupKey(group.status(), group.fallbackStep(), group.reason()),
                        runId,
                        group.status(),
                        group.fallbackStep(),
                        group.reason(),
                        group.count()
                ))
                .toList();
    }

    private String errorGroupKey(String status, String fallbackStep, String reason) {
        String raw = String.join("|",
                nullToEmpty(status),
                nullToEmpty(fallbackStep),
                nullToEmpty(reason)
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(Objects.hash(raw));
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record ErrorGroupView(String key, Long runId, String status, String fallbackStep, String reason, long count) {
    }
}
