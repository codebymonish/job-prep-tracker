package com.jobtracker.application.exception;

import com.jobtracker.application.entity.ApplicationStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    private final ApplicationStatus from;
    private final ApplicationStatus to;

    public InvalidStatusTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super(String.format("Cannot transition status from %s to %s", from, to));
        this.from = from;
        this.to = to;
    }

    public ApplicationStatus getFrom() {
        return from;
    }

    public ApplicationStatus getTo() {
        return to;
    }
}
