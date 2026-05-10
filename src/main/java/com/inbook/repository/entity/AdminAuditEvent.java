package com.inbook.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "admin_audit_event")
public class AdminAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = true)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @ManyToOne(optional = true)
    @JoinColumn(name = "target_user_id")
    private AppUser targetUser;

    @ManyToOne(optional = true)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private Long created_at;

    public AdminAuditEvent() {
    }

    public Long getId() { return id; }
    public AppUser getActor() { return actor; }
    public AppUser getTargetUser() { return targetUser; }
    public Institution getInstitution() { return institution; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Long getCreated_at() { return created_at; }

    public void setId(Long id) { this.id = id; }
    public void setActor(AppUser actor) { this.actor = actor; }
    public void setTargetUser(AppUser targetUser) { this.targetUser = targetUser; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public void setAction(String action) { this.action = action; }
    public void setDetails(String details) { this.details = details; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
}
