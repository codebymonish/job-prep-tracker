package com.jobtracker.application.exception;

public class StaleApplicationException extends RuntimeException {
    public StaleApplicationException() {
        super("Application was updated by another request. Please refresh and try again.");
    }

    public StaleApplicationException(String message) {
        super(message);
    }
}
