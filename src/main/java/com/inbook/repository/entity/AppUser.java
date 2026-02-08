package com.inbook.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    // Esempio semplice: ruoli separati da virgola, es: "ROLE_USER,ROLE_ADMIN"
    @Column(nullable = false, length = 200)
    private String roles;

    @Column(nullable = false)
    private boolean enabled = true;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, String roles, boolean enabled) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRoles() { return roles; }
    public boolean isEnabled() { return enabled; }

    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRoles(String roles) { this.roles = roles; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}