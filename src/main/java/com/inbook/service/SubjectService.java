package com.inbook.service;

import com.inbook.repository.SubjectRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectService {
    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    public Subject loadMateria(SchoolClass classe, AppUser docente, String nomeMateria, Long created_at, Long updated_at) {
        Subject s= new Subject();
        s.setClasse(classe);
        s.setDocente(docente);
        s.setNomeMateria(nomeMateria);
        s.setCreated_at(created_at != null ? created_at : System.currentTimeMillis());
        s.setUpdated_at(updated_at != null ? updated_at : System.currentTimeMillis());
        return subjectRepository.save(s);
    }

    public Subject modifySubject(Long id,SchoolClass classe,AppUser docente ,String nomeMateria, Long updated_at) {
        Subject s= subjectRepository.findById(id).orElseThrow(() -> new RuntimeException("classe non trovata"));
        s.setClasse(classe);
        s.setDocente(docente);
        s.setNomeMateria(nomeMateria);
        s.setUpdated_at(updated_at != null ? updated_at : System.currentTimeMillis());
        return subjectRepository.save(s);
    }

    public void deleteSubject(Long id) {
        if (subjectRepository.existsById(id)) {
            subjectRepository.deleteById(id);
        }
        else{
            throw new RuntimeException("materia non trovata");
        }
    }

    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }


}
