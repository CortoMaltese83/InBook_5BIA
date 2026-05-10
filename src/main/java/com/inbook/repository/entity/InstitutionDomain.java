package com.inbook.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "institution_domain", uniqueConstraints = {
        @UniqueConstraint(name = "uk_institution_domain", columnNames = "domain")
})
public class InstitutionDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false, length = 120)
    private String domain;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Long created_at;

    @Column(nullable = false)
    private Long updated_at;

    public InstitutionDomain() {
    }

    public Long getId() { return id; }
    public Institution getInstitution() { return institution; }
    public String getDomain() { return domain; }
    public boolean isActive() { return active; }
    public Long getCreated_at() { return created_at; }
    public Long getUpdated_at() { return updated_at; }

    public void setId(Long id) { this.id = id; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setActive(boolean active) { this.active = active; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
    public void setUpdated_at(Long updated_at) { this.updated_at = updated_at; }
}
