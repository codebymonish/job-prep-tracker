package com.jobtracker.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
    private final Optional<JavaMailSender> mailSender;
    
    @Value("${app.auth.frontend-base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public EmailService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    @Async("taskExecutor")
    public void sendVerificationEmail(UUID userId, String to, String rawToken) {
        if (mailSender.isEmpty()) {
            log.warn("Mail sender not configured — skipping verification email for userId={}", userId);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Verify your Job Tracker account");
            message.setText("Click the following link to verify your email address:\n"
                    + frontendBaseUrl + "/auth/verify?token=" + rawToken);
            
            mailSender.get().send(message);
            log.debug("Verification email sent to userId={}", userId);
        } catch (MailException e) {
            log.error("Failed to send verification email for userId={}", userId, e);
        }
    }

    @Async("taskExecutor")
    public void sendPasswordResetEmail(String to, String rawToken) {
        if (mailSender.isEmpty()) {
            log.warn("Mail sender not configured — skipping password reset email");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Reset your Job Tracker password");
            message.setText("Click the following link to reset your password:\n"
                    + frontendBaseUrl + "/auth/password-reset?token=" + rawToken);
            
            mailSender.get().send(message);
        } catch (MailException e) {
            log.error("Failed to send password reset email", e);
        }
    }
}
