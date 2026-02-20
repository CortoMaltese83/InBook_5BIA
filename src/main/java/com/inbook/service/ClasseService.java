package com.inbook.service;

import com.inbook.repository.SchoolClassRepository;
import com.inbook.repository.entity.SchoolClass;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClasseService {
    private final SchoolClassRepository repo;

    public ClasseService(SchoolClassRepository repo) {
        this.repo = repo;
    }

    public SchoolClass AddClass(String nome, String annoScolastico, String sezione, String stato, LocalDateTime created_at, LocalDateTime update_at) {

        SchoolClass c = new SchoolClass();
        c.setNome(nome);
        c.setAnnoScolastico(stato);
        c.setSezione(sezione);
        c.setStato(stato);
        c.setCreated_at(created_at);
        c.setUpdated_at(update_at);
        return repo.save(c);

    }

    public SchoolClass modifyClass(Long id, String nome, String annoScolastico, String sezione, String stato, LocalDateTime update_at) {
        SchoolClass c = repo.findById(id).orElseThrow(() -> new RuntimeException("classe non trovata"));
        c.setNome(nome);
        c.setAnnoScolastico(stato);
        c.setSezione(sezione);
        c.setStato(stato);
        c.setUpdated_at(update_at);
        return repo.save(c);
    }
    public void deleteClass(Long id){
        if (repo.existsById(id)){
            repo.deleteById(id);
        }
        else{
            throw new RuntimeException("classe non trovata");
        }

    }
    public List<SchoolClass> getAllClasses() {
        return repo.findAll();
    }
}



