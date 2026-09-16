package com.jobtracker.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;

import static org.mockito.Mockito.*;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        
        emailService = new EmailService(Optional.of(mailSender));
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "http://localhost:4200");
    }

    @Test
    void shouldSendVerificationEmail() {
        emailService.sendVerificationEmail(UUID.randomUUID(), "test@example.com", "test-token-123");
        verify(mailSender, timeout(1000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldNotThrowIfMailSenderAbsent() {
        EmailService emptyService = new EmailService(Optional.empty());
        ReflectionTestUtils.setField(emptyService, "frontendBaseUrl", "http://localhost:4200");
        
        // Should log warning but not throw
        emptyService.sendVerificationEmail(UUID.randomUUID(), "test@example.com", "test-token-123");
    }

    @Test
    void shouldCatchMailException() {
        doThrow(new MailException("SMTP down") {}).when(mailSender).send(any(SimpleMailMessage.class));
        
        // Async method should catch it internally
        emailService.sendVerificationEmail(UUID.randomUUID(), "test@example.com", "test-token-123");
        verify(mailSender, timeout(1000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldSendPasswordResetEmail() {
        emailService.sendPasswordResetEmail("test@example.com", "reset-token-123");
        verify(mailSender, timeout(1000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldNotThrowIfMailSenderAbsentForReset() {
        EmailService emptyService = new EmailService(Optional.empty());
        ReflectionTestUtils.setField(emptyService, "frontendBaseUrl", "http://localhost:4200");
        
        emptyService.sendPasswordResetEmail("test@example.com", "reset-token-123");
    }

    @Test
    void shouldCatchMailExceptionForReset() {
        doThrow(new MailException("SMTP down") {}).when(mailSender).send(any(SimpleMailMessage.class));
        
        emailService.sendPasswordResetEmail("test@example.com", "reset-token-123");
        verify(mailSender, timeout(1000)).send(any(SimpleMailMessage.class));
    }
}
