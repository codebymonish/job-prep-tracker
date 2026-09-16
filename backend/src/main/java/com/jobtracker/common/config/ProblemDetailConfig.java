package com.jobtracker.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;

@ControllerAdvice
public class ProblemDetailConfig {

    @Value("${app.error.base-url:https://api.jobtracker.app/errors}")
    private String errorBaseUrl;

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        if (ex.getMessage() != null && (ex.getMessage().contains("Invalid") || ex.getMessage().contains("Account"))) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
            problemDetail.setType(URI.create(errorBaseUrl + "/unauthorized"));
            problemDetail.setTitle("Unauthorized");
            problemDetail.setProperty("correlationId", MDC.get("correlationId"));
            return problemDetail;
        }
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(URI.create(errorBaseUrl + "/bad-request"));
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty("correlationId", MDC.get("correlationId"));
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setType(URI.create(errorBaseUrl + "/validation-failed"));
        problemDetail.setTitle("Validation Error");
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("correlationId", MDC.get("correlationId"));
        return problemDetail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalStateException(IllegalStateException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setType(URI.create(errorBaseUrl + "/conflict"));
        problemDetail.setTitle("Conflict");
        problemDetail.setProperty("correlationId", MDC.get("correlationId"));
        return problemDetail;
    }

    @ExceptionHandler(com.jobtracker.auth.exception.DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmailException(com.jobtracker.auth.exception.DuplicateEmailException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setType(URI.create(errorBaseUrl + "/email-exists"));
        problemDetail.setTitle("Duplicate Email");
        problemDetail.setProperty("correlationId", MDC.get("correlationId"));
        return problemDetail;
    }
}
