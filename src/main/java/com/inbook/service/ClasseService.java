package com.inbook.service;

import com.inbook.repository.SchoolClassRepository;
import com.inbook.repository.entity.SchoolClass;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClasseService {
    private final SchoolClassRepository repo;

    public ClasseService(SchoolClassRepository repo) {
        this.repo = repo;
    }

    public SchoolClass AddClass(String nome, String anno, String sezione, String stato, Long created_at, Long update_at) {

        SchoolClass c = new SchoolClass();
        c.setNome(anno + sezione);
        c.setAnno(anno);
        c.setSezione(sezione);
        c.setStato(stato);
        c.setCreated_at(created_at != null ? created_at : System.currentTimeMillis());
        c.setUpdated_at(update_at != null ? update_at : System.currentTimeMillis());
        return repo.save(c);

    }

    public SchoolClass modifyClass(Long id, String nome, String anno, String sezione, String stato, Long update_at) {
        SchoolClass c = repo.findById(id).orElseThrow(() -> new RuntimeException("classe non trovata"));
        c.setNome(anno + sezione);
        c.setAnno(anno);
        c.setSezione(sezione);
        c.setStato(stato);
        c.setUpdated_at(update_at != null ? update_at : System.currentTimeMillis());
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



