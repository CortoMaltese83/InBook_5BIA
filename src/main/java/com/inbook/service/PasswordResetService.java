package com.inbook.service;

import com.inbook.repository.AppUserRepository;
import com.inbook.repository.entity.AppUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {
    static final long RESET_REQUEST_INTERVAL_MS = 24L * 60 * 60 * 1000;
    static final long RESET_TOKEN_TTL_MS = 60L * 60 * 1000;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(AppUserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional
    public void requestTeacherPasswordReset(String email) {
        Optional<String> cleanEmail = normalizeEmail(email);
        if (cleanEmail.isEmpty()) {
            return;
        }

        Optional<AppUser> userResult = userRepository.findByEmailIgnoreCase(cleanEmail.get());
        if (userResult.isEmpty()) {
            return;
        }

        AppUser user = userResult.get();
        if (!canReceivePasswordReset(user)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRequest = user.getPasswordResetRequestedAt();
        if (lastRequest != null && lastRequest + RESET_REQUEST_INTERVAL_MS > now) {
            return;
        }

        String token = generateToken();
        user.setPasswordResetTokenHash(hashToken(token));
        user.setPasswordResetExpiresAt(now + RESET_TOKEN_TTL_MS);
        user.setPasswordResetRequestedAt(now);
        AppUser saved = userRepository.save(user);
        notificationService.sendPasswordResetEmail(saved, token);
    }

    @Transactional(readOnly = true)
    public boolean isResetTokenValid(String token) {
        Optional<AppUser> userResult = findByToken(token);
        if (userResult.isEmpty()) {
            return false;
        }

        AppUser user = userResult.get();
        return canReceivePasswordReset(user) && isTokenNotExpired(user);
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        AppUser user = findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token non valido o scaduto"));

        if (!canReceivePasswordReset(user) || !isTokenNotExpired(user)) {
            clearResetToken(user);
            userRepository.save(user);
            throw new IllegalArgumentException("Token non valido o scaduto");
        }

        String cleanPassword = requireValidPassword(newPassword);
        String cleanConfirmPassword = requireValidPassword(confirmPassword);
        if (!cleanPassword.equals(cleanConfirmPassword)) {
            throw new IllegalArgumentException("Le password non coincidono");
        }

        user.setPasswordHash(passwordEncoder.encode(cleanPassword));
        clearResetToken(user);
        userRepository.save(user);
    }

    private Optional<AppUser> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByPasswordResetTokenHash(hashToken(token.trim()));
    }

    private boolean canReceivePasswordReset(AppUser user) {
        return user != null
                && isTeacher(user)
                && user.isEnabled()
                && Boolean.TRUE.equals(user.getEmailVerified());
    }

    private boolean isTeacher(AppUser user) {
        return user.getRoles() != null
                && user.getRoles().toUpperCase(Locale.ITALIAN).contains("DOCENTE");
    }

    private boolean isTokenNotExpired(AppUser user) {
        Long expiresAt = user.getPasswordResetExpiresAt();
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }

    private void clearResetToken(AppUser user) {
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
    }

    private Optional<String> normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String cleanEmail = email.trim().toLowerCase(Locale.ITALIAN);
        if (!cleanEmail.contains("@") || cleanEmail.startsWith("@") || cleanEmail.endsWith("@")) {
            return Optional.empty();
        }
        return Optional.of(cleanEmail);
    }

    private String requireValidPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password mancante");
        }
        String cleanPassword = password.trim();
        if (cleanPassword.length() < 8) {
            throw new IllegalArgumentException("La password deve contenere almeno 8 caratteri");
        }
        return cleanPassword;
    }

    private String generateToken() {
        byte[] bytes = new byte[36];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
