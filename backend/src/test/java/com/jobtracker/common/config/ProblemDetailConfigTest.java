package com.jobtracker.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemDetailConfigTest {

    private ProblemDetailConfig config;

    @BeforeEach
    void setUp() {
        config = new ProblemDetailConfig();
        // Reflection or setter not needed if we set property, but there is no setter.
        // It's a @Value, so it might be null here, which becomes "null/unauthorized". We can use ReflectionTestUtils but let's see.
        org.springframework.test.util.ReflectionTestUtils.setField(config, "errorBaseUrl", "https://api.jobtracker.app/errors");
        MDC.put("correlationId", "test-corr-id-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid format");
        ProblemDetail pd = config.handleIllegalArgumentException(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(pd.getProperties()).isNotNull();
        assertThat(pd.getProperties()).containsEntry("correlationId", "test-corr-id-123");
    }

    @Test
    void testMethodArgumentNotValidException() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getAllErrors()).thenReturn(List.of(new FieldError("objectName", "field", "message")));
        
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        ProblemDetail pd = config.handleValidationExceptions(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties()).isNotNull();
        assertThat(pd.getProperties()).containsEntry("correlationId", "test-corr-id-123");
        assertThat((Map<String, String>) pd.getProperties().get("errors")).containsEntry("field", "message");
    }

    @Test
    void testIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Conflict occurred");
        ProblemDetail pd = config.handleIllegalStateException(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties()).isNotNull();
        assertThat(pd.getProperties()).containsEntry("correlationId", "test-corr-id-123");
    }
}
