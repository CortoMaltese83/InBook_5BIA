package com.inbook.service;

import com.inbook.repository.AppUserRepository;
import com.inbook.repository.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    @Test
    void requestCreatesTokenAndSendsEmailForVerifiedActiveTeacher() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        NotificationService notificationService = mock(NotificationService.class);
        PasswordResetService service = new PasswordResetService(userRepository, passwordEncoder, notificationService);

        AppUser teacher = activeVerifiedTeacher();
        when(userRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(Optional.of(teacher));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestTeacherPasswordReset("Teacher@Example.com");

        assertNotNull(teacher.getPasswordResetTokenHash());
        assertNotNull(teacher.getPasswordResetExpiresAt());
        assertNotNull(teacher.getPasswordResetRequestedAt());
        verify(userRepository).save(teacher);
        verify(notificationService).sendPasswordResetEmail(eq(teacher), anyString());
    }

    @Test
    void requestDoesNotSendAnotherEmailWithinOneDay() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        NotificationService notificationService = mock(NotificationService.class);
        PasswordResetService service = new PasswordResetService(userRepository, passwordEncoder, notificationService);

        AppUser teacher = activeVerifiedTeacher();
        teacher.setPasswordResetRequestedAt(System.currentTimeMillis());
        when(userRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(Optional.of(teacher));

        service.requestTeacherPasswordReset("teacher@example.com");

        verify(userRepository, never()).save(any(AppUser.class));
        verify(notificationService, never()).sendPasswordResetEmail(any(AppUser.class), anyString());
    }

    @Test
    void requestDoesNotSendEmailForUnverifiedTeacher() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        NotificationService notificationService = mock(NotificationService.class);
        PasswordResetService service = new PasswordResetService(userRepository, passwordEncoder, notificationService);

        AppUser teacher = activeVerifiedTeacher();
        teacher.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(Optional.of(teacher));

        service.requestTeacherPasswordReset("teacher@example.com");

        verify(userRepository, never()).save(any(AppUser.class));
        verify(notificationService, never()).sendPasswordResetEmail(any(AppUser.class), anyString());
    }

    @Test
    void resetPasswordUpdatesHashAndClearsToken() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        NotificationService notificationService = mock(NotificationService.class);
        PasswordResetService service = new PasswordResetService(userRepository, passwordEncoder, notificationService);

        AppUser teacher = activeVerifiedTeacher();
        when(userRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(Optional.of(teacher));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestTeacherPasswordReset("teacher@example.com");
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendPasswordResetEmail(any(AppUser.class), tokenCaptor.capture());
        when(userRepository.findByPasswordResetTokenHash(teacher.getPasswordResetTokenHash())).thenReturn(Optional.of(teacher));
        when(passwordEncoder.encode("newpass123")).thenReturn("encoded-password");

        service.resetPassword(tokenCaptor.getValue(), "newpass123", "newpass123");

        assertEquals("encoded-password", teacher.getPasswordHash());
        assertNull(teacher.getPasswordResetTokenHash());
        assertNull(teacher.getPasswordResetExpiresAt());
    }

    private AppUser activeVerifiedTeacher() {
        AppUser user = new AppUser();
        user.setEmail("teacher@example.com");
        user.setName("Teacher");
        user.setRoles("TYPE_DOCENTE");
        user.setEnabled(true);
        user.setEmailVerified(true);
        return user;
    }
}
