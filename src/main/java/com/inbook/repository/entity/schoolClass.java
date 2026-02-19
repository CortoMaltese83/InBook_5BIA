package com.inbook.repository.entity;
import jakarta.persistence.*;


@Entity
@Table(name = "Classe")
public class schoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(nullable = false,length = 2)
    private String nome;

    @Column(nullable = false,length = 7)
    private String annoScolastico;

    @Column(nullable = false,length = 3)
    private String sezione;

    @Column(nullable = false,length = 10) //"ATTIVA" O "ARCHIVIATA"
    private String stato;

    @Column(nullable = false,length = 30)
    private Long created_at;

    @Column(nullable = false,length = 30)
    private Long updated_at;

    public schoolClass() {}//è di default e non serve a niente :) (se lo togliete ci da errore)

    public schoolClass(Long id, String nome, String annoScolastico, String sezione, String stato, Long created_at, Long updated_at) {
        this.id = id;
        this.nome = nome;
        this.annoScolastico = annoScolastico;
        this.sezione = sezione;
        this.stato = stato;
        this.created_at = created_at;
        this.updated_at = updated_at;
    }

    //GETTER
    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getAnnoScolastico() { return annoScolastico; }
    public String getSezione() { return sezione; }
    public String getStato() { return stato; }
    public Long getCreated_at() { return created_at; }
    public Long getUpdated_at() { return updated_at; }

    //SETTER
    public void setId(Long id) {this.id = id;}
    public void setNome(String nome) {this.nome = nome;}
    public void setAnnoScolastico(String annoScolastico) {this.annoScolastico = annoScolastico;}
    public void setSezione(String sezione) {this.sezione = sezione;}
    public void setStato(String stato) {this.stato = stato;}
    public void setCreated_at(Long created_at) {this.created_at = created_at;}
    public void setUpdated_at(Long updated_at) {this.updated_at = updated_at;}
}
