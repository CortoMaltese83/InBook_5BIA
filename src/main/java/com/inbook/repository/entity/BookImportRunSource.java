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
@Table(name = "book_import_run_source")
public class BookImportRunSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private BookImportRun run;

    @Column(nullable = false, length = 1000)
    private String sourceUrl;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private long rowsRead;

    @Column(nullable = false)
    private long saved;

    @Column(nullable = false)
    private long skipped;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Long created_at;

    public BookImportRunSource() {
    }

    public Long getId() { return id; }
    public BookImportRun getRun() { return run; }
    public String getSourceUrl() { return sourceUrl; }
    public String getStatus() { return status; }
    public long getRowsRead() { return rowsRead; }
    public long getSaved() { return saved; }
    public long getSkipped() { return skipped; }
    public String getErrorMessage() { return errorMessage; }
    public Long getCreated_at() { return created_at; }

    public void setId(Long id) { this.id = id; }
    public void setRun(BookImportRun run) { this.run = run; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setStatus(String status) { this.status = status; }
    public void setRowsRead(long rowsRead) { this.rowsRead = rowsRead; }
    public void setSaved(long saved) { this.saved = saved; }
    public void setSkipped(long skipped) { this.skipped = skipped; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
}
