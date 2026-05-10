package com.inbook.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "institution", uniqueConstraints = {
        @UniqueConstraint(name = "uk_institution_code", columnNames = "code")
})
public class Institution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private Long created_at;

    @Column(nullable = false)
    private Long updated_at;

    public Institution() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getStatus() { return status; }
    public Long getCreated_at() { return created_at; }
    public Long getUpdated_at() { return updated_at; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCode(String code) { this.code = code; }
    public void setStatus(String status) { this.status = status; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
    public void setUpdated_at(Long updated_at) { this.updated_at = updated_at; }
}
