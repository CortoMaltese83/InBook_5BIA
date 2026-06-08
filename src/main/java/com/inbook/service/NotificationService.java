package com.inbook.service;

import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.TeacherInvitation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final String appBaseUrl;
    private final String from;

    public NotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                               @Value("${inbook.app-base-url:http://localhost:8080}") String appBaseUrl,
                               @Value("${inbook.mail.from:no-reply@inbook.local}") String from) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.appBaseUrl = trimTrailingSlash(appBaseUrl);
        this.from = from;
    }

    public void sendVerificationEmail(AppUser user) {
        String link = appBaseUrl + "/auth/verify?token=" + user.getEmailVerificationToken();
        String subject = "Verifica il tuo account InBook";
        String body = """
                Ciao %s,

                verifica il tuo account InBook aprendo questo link:
                %s

                Se non hai richiesto tu la registrazione, ignora questa email.
                """.formatted(user.getName(), link);
        sendOrLog(user.getEmail(), subject, body, link);
    }

    public void sendInvitationEmail(TeacherInvitation invitation) {
        String link = appBaseUrl + "/invite/accept?token=" + invitation.getToken();
        String subject = "Invito docente a InBook";
        String institutionName = invitation.getInstitution() != null ? invitation.getInstitution().getName() : "istituto";
        String body = """
                Sei stato invitato a registrarti su InBook per %s.

                Apri questo link per iniziare la registrazione:
                %s

                Il link scade automaticamente.
                """.formatted(institutionName, link);
        sendOrLog(invitation.getEmail(), subject, body, link);
    }

    public void sendPasswordResetEmail(AppUser user, String token) {
        String link = appBaseUrl + "/password-reset/reset?token=" + token;
        String subject = "Reimposta la password InBook";
        String body = """
                Ciao %s,

                apri questo link per reimpostare la password del tuo account InBook:
                %s

                Il link scade automaticamente. Se non hai richiesto tu questa operazione, ignora questa email.
                """.formatted(user.getName(), link);
        sendOrLog(user.getEmail(), subject, body, link);
    }

    private void sendOrLog(String to, String subject, String body, String link) {
        if (mailSender == null) {
            log.info("Email non inviata: JavaMailSender non configurato. Destinatario={}, Oggetto={}, Link={}", to, subject, link);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Invio email non riuscito. Destinatario={}, Oggetto={}, Link={}", to, subject, link, e);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
