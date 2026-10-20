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

    @Test
    void shouldReturnNeutralWhenNotStarted() {
        SafeLogRedactor unstarted = new SafeLogRedactor();
        FilterReply reply = unstarted.decide(null, logger, Level.INFO, "test@example.com", null, null);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void shouldReturnNeutralWhenAlreadyRedactedMarker() {
        Marker redactedMarker = org.slf4j.MarkerFactory.getMarker("REDACTED");
        FilterReply reply = redactor.decide(redactedMarker, logger, Level.INFO, "test@example.com", null, null);
        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void shouldRedactParamsArray() {
        String format = "User details: {}, id={}";
        Object[] params = new Object[]{"user@example.com", 123};
        FilterReply reply = redactor.decide(null, logger, Level.INFO, format, params, null);

        assertThat(reply).isEqualTo(FilterReply.DENY);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(logger).info(any(Marker.class), eq(format), paramsCaptor.capture());
        Object[] captured = paramsCaptor.getValue();
        assertThat(captured[0]).isEqualTo("[EMAIL_REDACTED]");
        assertThat(captured[1]).isEqualTo(123);
    }

    @Test
    void shouldRedactTraceAndErrorLevels() {
        FilterReply replyTrace = redactor.decide(null, logger, Level.TRACE, "trace user@example.com", null, null);
        assertThat(replyTrace).isEqualTo(FilterReply.DENY);
        verify(logger).trace(any(Marker.class), eq("trace [EMAIL_REDACTED]"), (Object[]) eq(null));

        FilterReply replyError = redactor.decide(null, logger, Level.ERROR, "error user@example.com", null, null);
        assertThat(replyError).isEqualTo(FilterReply.DENY);
        verify(logger).error(any(Marker.class), eq("error [EMAIL_REDACTED]"), (Object[]) eq(null));
    }
}
