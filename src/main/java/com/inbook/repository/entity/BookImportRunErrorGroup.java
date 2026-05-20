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
@Table(name = "book_import_run_error_group")
public class BookImportRunErrorGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BookImportRun run;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 60)
    private String fallbackStep;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private long itemCount;

    @Column(nullable = false)
    private Long created_at;

    @Column(nullable = false)
    private Long updated_at;

    public BookImportRunErrorGroup() {
    }

    public Long getId() { return id; }
    public BookImportRun getRun() { return run; }
    public String getStatus() { return status; }
    public String getFallbackStep() { return fallbackStep; }
    public String getReason() { return reason; }
    public long getItemCount() { return itemCount; }
    public Long getCreated_at() { return created_at; }
    public Long getUpdated_at() { return updated_at; }

    public void setId(Long id) { this.id = id; }
    public void setRun(BookImportRun run) { this.run = run; }
    public void setStatus(String status) { this.status = status; }
    public void setFallbackStep(String fallbackStep) { this.fallbackStep = fallbackStep; }
    public void setReason(String reason) { this.reason = reason; }
    public void setItemCount(long itemCount) { this.itemCount = itemCount; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
    public void setUpdated_at(Long updated_at) { this.updated_at = updated_at; }
}
