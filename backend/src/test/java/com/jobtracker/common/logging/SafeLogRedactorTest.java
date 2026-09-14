package com.jobtracker.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SafeLogRedactorTest {

    private SafeLogRedactor redactor;
    private Logger logger;

    @BeforeEach
    void setUp() {
        redactor = new SafeLogRedactor();
        redactor.start();
        logger = mock(Logger.class);
    }

    @Test
    void shouldRedactJwtPattern() {
        String format = "User logged in with token eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        FilterReply reply = redactor.decide(null, logger, Level.INFO, format, null, null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).info(any(Marker.class), captor.capture(), (Object[]) eq(null));
        assertThat(captor.getValue()).isEqualTo("User logged in with token [JWT_REDACTED]");
    }

    @Test
    void shouldRedactEmailPattern() {
        String format = "Failed login for user@example.com";
        FilterReply reply = redactor.decide(null, logger, Level.WARN, format, null, null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).warn(any(Marker.class), captor.capture(), (Object[]) eq(null));
        assertThat(captor.getValue()).isEqualTo("Failed login for [EMAIL_REDACTED]");
    }

    @Test
    void shouldRedactTokenQueryParam() {
        String format = "Request to /auth/verify?token=12345-67890-abcde";
        FilterReply reply = redactor.decide(null, logger, Level.DEBUG, format, null, null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).debug(any(Marker.class), captor.capture(), (Object[]) eq(null));
        assertThat(captor.getValue()).isEqualTo("Request to /auth/verify?token=[TOKEN_REDACTED]");
    }

    @Test
    void shouldRedactAmpersandTokenQueryParam() {
        String format = "Request to /auth/verify?other=value&token=12345-67890-abcde";
        FilterReply reply = redactor.decide(null, logger, Level.DEBUG, format, null, null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger).debug(any(Marker.class), captor.capture(), (Object[]) eq(null));
        assertThat(captor.getValue()).isEqualTo("Request to /auth/verify?other=value&token=[TOKEN_REDACTED]");
    }

    @Test
    void shouldPassThroughNonSensitiveStringsUnchanged() {
        String format = "User logged out";
        FilterReply reply = redactor.decide(null, logger, Level.INFO, format, null, null);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
        verifyNoInteractions(logger);
    }
}
