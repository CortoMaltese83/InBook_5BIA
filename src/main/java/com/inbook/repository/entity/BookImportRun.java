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
@Table(name = "book_import_run")
public class BookImportRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Column(nullable = false)
    private Long started_at;

    private Long finished_at;

    @Column(nullable = false)
    private int sourceCount;

    @Column(nullable = false)
    private long rowsRead;

    @Column(nullable = false)
    private long totalItems;

    @Column(nullable = false)
    private long processed;

    @Column(nullable = false)
    private long saved;

    @Column(nullable = false)
    private long updated;

    @Column(nullable = false)
    private long found;

    @Column(nullable = false)
    private long skipped;

    @Column(nullable = false)
    private long failed;

    @Column(length = 1000)
    private String errorMessage;

    public BookImportRun() {
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public AppUser getActor() { return actor; }
    public Long getStarted_at() { return started_at; }
    public Long getFinished_at() { return finished_at; }
    public int getSourceCount() { return sourceCount; }
    public long getRowsRead() { return rowsRead; }
    public long getTotalItems() { return totalItems; }
    public long getProcessed() { return processed; }
    public long getSaved() { return saved; }
    public long getUpdated() { return updated; }
    public long getFound() { return found; }
    public long getSkipped() { return skipped; }
    public long getFailed() { return failed; }
    public String getErrorMessage() { return errorMessage; }

    public void setId(Long id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setActor(AppUser actor) { this.actor = actor; }
    public void setStarted_at(Long started_at) { this.started_at = started_at; }
    public void setFinished_at(Long finished_at) { this.finished_at = finished_at; }
    public void setSourceCount(int sourceCount) { this.sourceCount = sourceCount; }
    public void setRowsRead(long rowsRead) { this.rowsRead = rowsRead; }
    public void setTotalItems(long totalItems) { this.totalItems = totalItems; }
    public void setProcessed(long processed) { this.processed = processed; }
    public void setSaved(long saved) { this.saved = saved; }
    public void setUpdated(long updated) { this.updated = updated; }
    public void setFound(long found) { this.found = found; }
    public void setSkipped(long skipped) { this.skipped = skipped; }
    public void setFailed(long failed) { this.failed = failed; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
