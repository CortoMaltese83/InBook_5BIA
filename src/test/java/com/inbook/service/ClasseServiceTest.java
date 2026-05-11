package com.inbook.service;

import com.inbook.repository.BookRepository;
import com.inbook.repository.SchoolClassRepository;
import com.inbook.repository.SubjectRepository;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.Book;
import com.inbook.repository.entity.Institution;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClasseServiceTest {
    private final ClasseService service = new ClasseService(null, null, null, null);

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

    @Test
    void deleteClassDeletesSubjectsAndUnreferencedBooks() {
        SchoolClassRepository classRepository = mock(SchoolClassRepository.class);
        SubjectRepository subjectRepository = mock(SubjectRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        ClasseService service = new ClasseService(classRepository, null, subjectRepository, bookRepository);

        SchoolClass schoolClass = new SchoolClass();
        Subject subject = subjectWithBook("9781234567890");
        List<Subject> subjects = List.of(subject);

        when(classRepository.findById(1L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findByClasse_Id(1L)).thenReturn(subjects);
        when(subjectRepository.existsByBook_Isbn("9781234567890")).thenReturn(false);

        service.deleteClass(1L);

        InOrder ordered = inOrder(subjectRepository, classRepository, bookRepository);
        ordered.verify(subjectRepository).deleteAll(subjects);
        ordered.verify(subjectRepository).flush();
        ordered.verify(classRepository).delete(schoolClass);
        ordered.verify(classRepository).flush();
        ordered.verify(bookRepository).deleteByIsbn("9781234567890");
    }

    @Test
    void deleteClassKeepsBooksStillUsedByOtherSubjects() {
        SchoolClassRepository classRepository = mock(SchoolClassRepository.class);
        SubjectRepository subjectRepository = mock(SubjectRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        ClasseService service = new ClasseService(classRepository, null, subjectRepository, bookRepository);

        SchoolClass schoolClass = new SchoolClass();
        Subject subject = subjectWithBook("9781234567890");

        when(classRepository.findById(1L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findByClasse_Id(1L)).thenReturn(List.of(subject));
        when(subjectRepository.existsByBook_Isbn("9781234567890")).thenReturn(true);

        service.deleteClass(1L);

        verify(bookRepository, never()).deleteByIsbn("9781234567890");
    }

    private AppUser userWithRole(String role) {
        AppUser user = new AppUser();
        user.setRoles(role);
        return user;
    }

    private Subject subjectWithBook(String isbn) {
        Book book = new Book();
        book.setIsbn(isbn);
        Subject subject = new Subject();
        subject.setBook(book);
        return subject;
    }
}
