package com.inbook.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "book_import_run_item")
public class BookImportRunItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BookImportRun run;

    private Long bookId;

    @Column(length = 20)
    private String isbn;

    @Column(length = 13)
    private String normalizedIsbn;

    @Column(length = 1000)
    private String sourceUrl;

    private Long rowNumber;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 60)
    private String fallbackStep;

    @Column(length = 1000)
    private String reason;

    @Column(length = 300)
    private String title;

    @Column(nullable = false)
    private Long created_at;

    public BookImportRunItem() {
    }

    public Long getId() { return id; }
    public BookImportRun getRun() { return run; }
    public Long getBookId() { return bookId; }
    public String getIsbn() { return isbn; }
    public String getNormalizedIsbn() { return normalizedIsbn; }
    public String getSourceUrl() { return sourceUrl; }
    public Long getRowNumber() { return rowNumber; }
    public String getStatus() { return status; }
    public String getFallbackStep() { return fallbackStep; }
    public String getReason() { return reason; }
    public String getTitle() { return title; }
    public Long getCreated_at() { return created_at; }

    public void setId(Long id) { this.id = id; }
    public void setRun(BookImportRun run) { this.run = run; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setNormalizedIsbn(String normalizedIsbn) { this.normalizedIsbn = normalizedIsbn; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setRowNumber(Long rowNumber) { this.rowNumber = rowNumber; }
    public void setStatus(String status) { this.status = status; }
    public void setFallbackStep(String fallbackStep) { this.fallbackStep = fallbackStep; }
    public void setReason(String reason) { this.reason = reason; }
    public void setTitle(String title) { this.title = title; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
}
