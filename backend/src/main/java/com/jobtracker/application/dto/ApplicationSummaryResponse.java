package com.jobtracker.application.dto;

import com.jobtracker.application.entity.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationSummaryResponse(
    UUID id,
    String company,
    String roleTitle,
    ApplicationStatus status,
    LocalDate appliedDate,
    int version,
    Instant createdAt
) {}
