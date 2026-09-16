package com.jobtracker.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetDto(
    @NotBlank String token,

    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = ".*[A-Z].*", message = "must contain at least one uppercase letter")
    @Pattern(regexp = ".*\\d.*", message = "must contain at least one digit")
    String newPassword
) {}
