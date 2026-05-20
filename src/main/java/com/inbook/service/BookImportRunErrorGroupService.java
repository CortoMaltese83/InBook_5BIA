package com.inbook.service;

import com.inbook.repository.BookImportRunErrorGroupRepository;
import com.inbook.repository.entity.BookImportRun;
import com.inbook.repository.entity.BookImportRunErrorGroup;
import org.springframework.stereotype.Service;

@Service
public class BookImportRunErrorGroupService {
    private final BookImportRunErrorGroupRepository repository;

    public BookImportRunErrorGroupService(BookImportRunErrorGroupRepository repository) {
        this.repository = repository;
    }

    public void increment(BookImportRun run, String status, String fallbackStep, String reason) {
        increment(run, status, fallbackStep, reason, 1);
    }

    public void increment(BookImportRun run, String status, String fallbackStep, String reason, long amount) {
        if (run == null || amount <= 0) {
            return;
        }
        String cleanStatus = truncate(status == null || status.isBlank() ? "DISCARDED" : status, 30);
        String cleanFallback = truncate(clean(fallbackStep), 60);
        String cleanReason = truncate(clean(reason), 1000);
        long now = System.currentTimeMillis();
        BookImportRunErrorGroup group = repository
                .findByRunAndStatusAndFallbackStepAndReason(run, cleanStatus, cleanFallback, cleanReason)
                .orElseGet(() -> {
                    BookImportRunErrorGroup newGroup = new BookImportRunErrorGroup();
                    newGroup.setRun(run);
                    newGroup.setStatus(cleanStatus);
                    newGroup.setFallbackStep(cleanFallback);
                    newGroup.setReason(cleanReason);
                    newGroup.setCreated_at(now);
                    newGroup.setItemCount(0);
                    return newGroup;
                });
        group.setItemCount(group.getItemCount() + amount);
        group.setUpdated_at(now);
        repository.save(group);
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
