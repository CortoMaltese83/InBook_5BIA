package com.inbook.service;

import com.inbook.repository.SchoolClassRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.SchoolClassRepository;
import org.springframework.stereotype.Service;

import static org.aspectj.weaver.tools.cache.SimpleCacheFactory.enabled;

@Service
public class ClasseService {
    private final SchoolClassRepository repo;

    public ClasseService(SchoolClassRepository repo) {
        this.repo = repo;
    }

    public SchoolClass AddClass (String nome, String annoScolastico, String sezione, String stato){

        SchoolClass c = new SchoolClass();
        c.setNome(nome);
        c.setAnnoScolastico(stato);
        c.setSezione(sezione);
        c.setStato(stato);
        return repo.save(c);

    }
        public SchoolClass modifyClass(int id,String nome,String annoScolastico, String sezione, String stato){

            System.out.println("service per modificare una classe");
            return null;
        }

    public SchoolClass DeleteClass(String nome,String annoScolastico, String sezione, String stato){

        System.out.println("service per modificare una classe");
        return null;

    }
}



