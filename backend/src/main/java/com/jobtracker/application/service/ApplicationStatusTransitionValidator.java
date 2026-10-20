package com.jobtracker.application.service;

import com.jobtracker.application.entity.ApplicationStatus;
import com.jobtracker.application.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static com.jobtracker.application.entity.ApplicationStatus.*;

@Service
public class ApplicationStatusTransitionValidator {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
        APPLIED,      Set.of(INTERVIEWING, REJECTED, WITHDRAWN),
        INTERVIEWING, Set.of(OFFER, REJECTED, WITHDRAWN),
        OFFER,        Set.of(),
        REJECTED,     Set.of(),
        WITHDRAWN,    Set.of()
    );

    public void validate(ApplicationStatus from, ApplicationStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidStatusTransitionException(from, to);
        }
    }
}
