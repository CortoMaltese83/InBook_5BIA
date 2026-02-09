package com.inbook.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=100, unique = true)
    private String email;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Column(nullable=false, length=50)
    private String name;

    @Column(nullable=false, length=50)
    private String surname;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    // Esempio semplice: ruoli separati da virgola, es: "ROLE_USER,ROLE_ADMIN"
    @Column(nullable = false, length = 200)
    private String roles;

    @Column(nullable = false)
    private boolean enabled = true;

    public AppUser() {}

    public AppUser(String email, String passwordHash, String name, String surname, String username, String roles, boolean enabled) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.surname = surname;
        this.username = username;
        this.roles = roles;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getSurname() { return surname; }
    public String getUsername() { return username; }
    public String getRoles() { return roles; }
    public boolean isEnabled() { return enabled; }

    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setName(String name) { this.name = name; }
    public void setSurname(String surname) { this.surname = surname; }
    public void setUsername(String username) { this.username = username; }
    public void setRoles(String roles) { this.roles = roles; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

}