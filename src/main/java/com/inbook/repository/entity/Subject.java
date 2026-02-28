package com.inbook.repository.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "materia")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "classe_id", nullable = false)
    private SchoolClass classe;


    @ManyToOne
    @JoinColumn(name = "docente_id", nullable = false)
    private AppUser docente;

    @Column(name = "nome_materia", nullable = false, length = 100)
    private String nomeMateria;

    @Column(nullable = false)
    private Long created_at;

    @Column(nullable = false)
    private Long updated_at;

    public Subject() {}

    public Subject(SchoolClass classe, AppUser docente, String nomeMateria, Long created_at, Long updated_at) {
        this.classe = classe;
        this.docente = docente;
        this.nomeMateria = nomeMateria;
        this.created_at = created_at;
        this.updated_at = updated_at;
    }

    // GETTER
    public Long getId() { return id; }
    public SchoolClass getClasse() { return classe; }
    public AppUser getDocente() { return docente; }
    public String getNomeMateria() { return nomeMateria; }
    public Long getCreated_at() { return created_at; }
    public Long getUpdated_at() { return updated_at; }

    // SETTER
    public void setId(Long id) { this.id = id; }
    public void setClasse(SchoolClass classe) { this.classe = classe; }
    public void setDocente(AppUser docente) { this.docente = docente; }
    public void setNomeMateria(String nomeMateria) { this.nomeMateria = nomeMateria; }
    public void setCreated_at(Long created_at) { this.created_at = created_at; }
    public void setUpdated_at(Long updated_at) { this.updated_at = updated_at; }
}