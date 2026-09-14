package com.jobtracker.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String name,
    String role,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
