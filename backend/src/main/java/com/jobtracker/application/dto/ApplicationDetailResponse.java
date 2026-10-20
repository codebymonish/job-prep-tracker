package com.jobtracker.application.dto;

import com.jobtracker.application.entity.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationDetailResponse(
    UUID id,
    String company,
    String roleTitle,
    String jdText,
    ApplicationStatus status,
    LocalDate appliedDate,
    int version,
    Instant createdAt,
    Instant updatedAt
) {}
