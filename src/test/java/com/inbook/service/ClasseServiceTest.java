package com.inbook.service;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Institution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasseServiceTest {
    private final ClasseService service = new ClasseService(null, null);

    @Test
    void teacherWithoutInstitutionCannotCreateClass() {
        AppUser teacher = userWithRole("TYPE_DOCENTE");

        assertFalse(service.canCreateClass(teacher));
    }

    @Test
    void teacherWithActiveInstitutionCanCreateClass() {
        AppUser teacher = userWithRole("TYPE_DOCENTE");
        Institution institution = new Institution();
        institution.setStatus("ACTIVE");
        teacher.setInstitution(institution);

        assertTrue(service.canCreateClass(teacher));
    }

    @Test
    void adminCanCreateClassWithoutInstitution() {
        AppUser admin = userWithRole("TYPE_ADMIN");

        assertTrue(service.canCreateClass(admin));
    }

    private AppUser userWithRole(String role) {
        AppUser user = new AppUser();
        user.setRoles(role);
        return user;
    }
}
