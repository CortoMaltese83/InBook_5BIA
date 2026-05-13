package com.inbook.service;

import com.inbook.dto.BookLookupResult;
import com.inbook.repository.BookImportRunItemRepository;
import com.inbook.repository.BookImportRunRepository;
import com.inbook.repository.BookRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Book;
import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class BookIsbnFallbackBatchService {
    private static final String TYPE = "ISBN_FALLBACK_BATCH";

    private final BookRepository bookRepository;
    private final BookLookupService bookLookupService;
    private final BookImportRunRepository runRepository;
    private final BookImportRunItemRepository itemRepository;
    private final BookImportRunErrorGroupService errorGroupService;

    public BookIsbnFallbackBatchService(BookRepository bookRepository,
                                        BookLookupService bookLookupService,
                                        BookImportRunRepository runRepository,
                                        BookImportRunItemRepository itemRepository,
                                        BookImportRunErrorGroupService errorGroupService) {
        this.bookRepository = bookRepository;
        this.bookLookupService = bookLookupService;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.errorGroupService = errorGroupService;
    }

    @Transactional
    public BookImportRun runFallbackBatch(AppUser actor) {
        BookImportRun run = startRun(actor);
        try {
            List<Book> books = bookRepository.findAll();
            run.setTotalItems(books.size());
            runRepository.save(run);

            for (Book book : books) {
                processBook(run, book);
            }

            run.setStatus(run.getFailed() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        } catch (RuntimeException e) {
            run.setStatus("FAILED");
            run.setErrorMessage(truncate(e.getMessage(), 1000));
        } finally {
            run.setFinished_at(System.currentTimeMillis());
        }
        return runRepository.save(run);
    }

    private BookImportRun startRun(AppUser actor) {
        BookImportRun run = new BookImportRun();
        run.setType(TYPE);
        run.setStatus("RUNNING");
        run.setActor(actor);
        run.setStarted_at(System.currentTimeMillis());
        return runRepository.save(run);
    }

    private void processBook(BookImportRun run, Book book) {
        run.setProcessed(run.getProcessed() + 1);
        String rawIsbn = book.getIsbn();
        try {
            BookLookupService.LookupTrace trace = bookLookupService.lookupByIsbnWithTrace(rawIsbn);
            BookLookupResult result = trace.result();
            boolean changed = applyLookupResult(book, result);
            if (changed) {
                bookRepository.save(book);
                run.setUpdated(run.getUpdated() + 1);
            }
            run.setFound(run.getFound() + 1);

            BookImportRunItem item = baseItem(run, book.getId(), rawIsbn, trace.normalizedIsbn());
            item.setStatus(changed ? "UPDATED" : "FOUND");
            item.setFallbackStep(foundStep(trace, result));
            item.setTitle(result.getTitolo());
            item.setReason(truncate((changed ? "Libro aggiornato. " : "Dati gia allineati. ") + stepsToText(trace.steps()), 1000));
            itemRepository.save(item);
        } catch (IllegalArgumentException e) {
            run.setSkipped(run.getSkipped() + 1);
            saveDiscardedItem(run, book, rawIsbn, null, "VALIDATION", e.getMessage());
        } catch (BookLookupService.BookNotFoundException e) {
            run.setSkipped(run.getSkipped() + 1);
            saveDiscardedItem(run, book, rawIsbn, e.getNormalizedIsbn(), "NOT_FOUND",
                    e.getMessage() + " " + stepsToText(e.getSteps()));
        } catch (BookLookupService.BookLookupUnavailableException e) {
            run.setFailed(run.getFailed() + 1);
            saveDiscardedItem(run, book, rawIsbn, e.getNormalizedIsbn(), "REMOTE_UNAVAILABLE",
                    e.getMessage() + " " + stepsToText(e.getSteps()));
        } catch (RuntimeException e) {
            run.setFailed(run.getFailed() + 1);
            saveDiscardedItem(run, book, rawIsbn, null, "ERROR", e.getMessage());
        }
    }

    private boolean applyLookupResult(Book book, BookLookupResult result) {
        boolean changed = false;
        changed |= setIfPresent(result.getAutore(), book.getAutore(), book::setAutore);
        changed |= setIfPresent(result.getTitolo(), book.getTitolo(), book::setTitolo);
        changed |= setIfPresent(result.getCasaEditrice(), book.getCasaEditrice(), book::setCasaEditrice);

        Integer volume = result.getVolume();
        if (volume != null && volume >= 0 && volume != book.getVolume()) {
            book.setVolume(volume);
            changed = true;
        }

        Double price = result.getPrezzo();
        if (price != null && price >= 0 && Double.compare(price, book.getPrezzo()) != 0) {
            book.setPrezzo(price);
            changed = true;
        }
        return changed;
    }

    private boolean setIfPresent(String newValue, String oldValue, java.util.function.Consumer<String> setter) {
        String cleaned = clean(newValue);
        if (cleaned == null || Objects.equals(cleaned, oldValue)) {
            return false;
        }
        setter.accept(cleaned);
        return true;
    }

    private void saveDiscardedItem(BookImportRun run, Book book, String rawIsbn, String normalizedIsbn,
                                   String fallbackStep, String reason) {
        errorGroupService.increment(run, "DISCARDED", fallbackStep, reason);
        BookImportRunItem item = baseItem(run, book.getId(), rawIsbn, normalizedIsbn);
        item.setStatus("DISCARDED");
        item.setFallbackStep(fallbackStep);
        item.setTitle(book.getTitolo());
        item.setReason(truncate(reason, 1000));
        itemRepository.save(item);
    }

    private BookImportRunItem baseItem(BookImportRun run, Long bookId, String isbn, String normalizedIsbn) {
        BookImportRunItem item = new BookImportRunItem();
        item.setRun(run);
        item.setBookId(bookId);
        item.setIsbn(truncate(isbn, 20));
        item.setNormalizedIsbn(truncate(normalizedIsbn, 13));
        item.setCreated_at(System.currentTimeMillis());
        return item;
    }

    private String foundStep(BookLookupService.LookupTrace trace, BookLookupResult result) {
        String provider = trace.steps().stream()
                .filter(step -> "FOUND".equals(step.status()))
                .map(BookLookupService.LookupStep::provider)
                .findFirst()
                .orElse(result.getSource());
        if ("LOCAL_CACHE".equals(provider) && result.getSource() != null && !result.getSource().isBlank()) {
            return "LOCAL_CACHE / " + result.getSource();
        }
        return provider;
    }

    private String stepsToText(List<BookLookupService.LookupStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (BookLookupService.LookupStep step : steps) {
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(step.provider()).append(":").append(step.status());
            if (step.reason() != null && !step.reason().isBlank()) {
                builder.append(" (").append(step.reason()).append(')');
            }
        }
        return builder.toString();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
