package com.inbook.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "teacher_invitation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_teacher_invitation_token", columnNames = "token")
})
public class TeacherInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 120)
    private String token;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @ManyToOne(optional = true)
    @JoinColumn(name = "invited_by_id")
    private AppUser invitedBy;

    @Column(nullable = false)
    private Long expires_at;

    @Column(nullable = false)
    private Long created_at;

    private Long accepted_at;
    private Long revoked_at;

    public TeacherInvitation() {
    }

    public Long getId() { return id; }
    public Institution getInstitution() { return institution; }
    public String getEmail() { return email; }
    public String getToken() { return token; }
    public String getStatus() { return status; }
    public AppUser getInvitedBy() { return invitedBy; }
    public Long getExpires_at() { return expires_at; }
    public Long getCreated_at() { return created_at; }
    public Long getAccepted_at() { return accepted_at; }
    public Long getRevoked_at() { return revoked_at; }

    public void setId(Long id) { this.id = id; }
    public void setInstitution(Institution institution) { this.institution = institution; }
    public void setEmail(String email) { this.email = email; }
    public void setToken(String token) { this.token = token; }
    public void setStatus(String status) { this.status = status; }
    public void setInvitedBy(AppUser invitedBy) { this.invitedBy = invitedBy; }
    public void setExpires_at(Long expires_at) { this.expires_at = expires_at; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
    public void setAccepted_at(Long accepted_at) { this.accepted_at = accepted_at; }
    public void setRevoked_at(Long revoked_at) { this.revoked_at = revoked_at; }
}
