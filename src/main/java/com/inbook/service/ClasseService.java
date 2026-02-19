package com.inbook.service;

import com.inbook.repository.SchoolClassRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.SchoolClassRepository;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.aspectj.weaver.tools.cache.SimpleCacheFactory.enabled;

@Service
public class ClasseService {
    private final SchoolClassRepository repo;

    public ClasseService(SchoolClassRepository repo) {
        this.repo = repo;
    }

    public SchoolClass AddClass(String nome, String annoScolastico, String sezione, String stato) {

        SchoolClass c = new SchoolClass();
        c.setNome(nome);
        c.setAnnoScolastico(stato);
        c.setSezione(sezione);
        c.setStato(stato);
        return repo.save(c);

    }

    public SchoolClass modifyClass(Long id, String nome, String annoScolastico, String sezione, String stato) {
        SchoolClass c = repo.findById(id).orElseThrow(() -> new RuntimeException("classe non trovata"));
        c.setNome(nome);
        c.setAnnoScolastico(stato);
        c.setSezione(sezione);
        c.setStato(stato);
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



