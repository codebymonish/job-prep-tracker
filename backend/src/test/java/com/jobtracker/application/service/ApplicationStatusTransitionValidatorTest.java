package com.jobtracker.application.service;

import com.jobtracker.application.entity.ApplicationStatus;
import com.jobtracker.application.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.jobtracker.application.entity.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStatusTransitionValidatorTest {

    private ApplicationStatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ApplicationStatusTransitionValidator();
    }

    private static Stream<Arguments> validTransitions() {
        return Stream.of(
            Arguments.of(APPLIED, INTERVIEWING),
            Arguments.of(APPLIED, REJECTED),
            Arguments.of(APPLIED, WITHDRAWN),
            Arguments.of(INTERVIEWING, OFFER),
            Arguments.of(INTERVIEWING, REJECTED),
            Arguments.of(INTERVIEWING, WITHDRAWN)
        );
    }

    @ParameterizedTest(name = "Valid transition from {0} to {1}")
    @MethodSource("validTransitions")
    void testValidTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThatCode(() -> validator.validate(from, to)).doesNotThrowAnyException();
    }

    private static Stream<Arguments> invalidTransitions() {
        List<Arguments> invalid = new ArrayList<>();
        // All possible transitions
        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                boolean isValid = (from == APPLIED && (to == INTERVIEWING || to == REJECTED || to == WITHDRAWN))
                        || (from == INTERVIEWING && (to == OFFER || to == REJECTED || to == WITHDRAWN));
                if (!isValid) {
                    invalid.add(Arguments.of(from, to));
                }
            }
        }
        return invalid.stream();
    }

    @ParameterizedTest(name = "Invalid transition from {0} to {1}")
    @MethodSource("invalidTransitions")
    void testInvalidTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThatThrownBy(() -> validator.validate(from, to))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .satisfies(ex -> {
                    InvalidStatusTransitionException e = (InvalidStatusTransitionException) ex;
                    assertThat(e.getFrom()).isEqualTo(from);
                    assertThat(e.getTo()).isEqualTo(to);
                });
    }
}
