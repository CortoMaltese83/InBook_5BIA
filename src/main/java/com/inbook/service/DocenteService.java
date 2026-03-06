package com.inbook.service;

import com.inbook.repository.DocenteRepository;
import com.inbook.repository.entity.SchoolClass;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocenteService {

    private final DocenteRepository docenteRepository;

    public DocenteService(DocenteRepository docenteRepository) {
        this.docenteRepository = docenteRepository;
    }

    public List<SchoolClass> getClassiByDocenteId(Long docenteId) {
        return docenteRepository.findByDocenteId(docenteId);
    }
}