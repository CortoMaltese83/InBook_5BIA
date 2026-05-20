package com.inbook.service;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Institution;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectServiceTest {
    private final SubjectService service = new SubjectService(null, null, null);

    @Test
    void teacherCanViewSubjectsForClassesInOwnInstitution() {
        Institution institution = institution(1L);
        AppUser teacher = teacher(10L, institution);
        SchoolClass schoolClass = schoolClass(institution);

        assertTrue(service.canAccessClass(teacher, schoolClass));
    }

    @Test
    void teacherCannotViewSubjectsForClassesInAnotherInstitution() {
        AppUser teacher = teacher(10L, institution(1L));
        SchoolClass schoolClass = schoolClass(institution(2L));

        assertFalse(service.canAccessClass(teacher, schoolClass));
    }

    @Test
    void teacherCanModifyOnlyOwnSubject() {
        AppUser owner = teacher(10L, institution(1L));
        AppUser otherTeacher = teacher(11L, institution(1L));
        Subject subject = new Subject();
        subject.setDocente(owner);

        assertTrue(service.canModifySubject(owner, subject));
        assertFalse(service.canModifySubject(otherTeacher, subject));
    }

    @Test
    void adminCanViewAndModifyAllSubjects() {
        AppUser admin = user(1L, "TYPE_ADMIN", null);
        Subject subject = new Subject();
        subject.setDocente(teacher(10L, institution(1L)));

        assertTrue(service.canAccessClass(admin, schoolClass(institution(2L))));
        assertTrue(service.canModifySubject(admin, subject));
    }

    private SchoolClass schoolClass(Institution institution) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setInstitution(institution);
        return schoolClass;
    }

    private Institution institution(Long id) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setStatus("ACTIVE");
        return institution;
    }

    private AppUser teacher(Long id, Institution institution) {
        return user(id, "TYPE_DOCENTE", institution);
    }

    private AppUser user(Long id, String role, Institution institution) {
        AppUser user = new AppUser();
        setId(user, id);
        user.setRoles(role);
        user.setInstitution(institution);
        return user;
    }

    private void setId(AppUser user, Long id) {
        try {
            Field field = AppUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
