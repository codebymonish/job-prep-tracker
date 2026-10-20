package com.jobtracker.application.dto;

import com.jobtracker.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateApplicationStatusRequest(
    @NotNull
    ApplicationStatus status,

    @NotNull
    Integer version
) {}
