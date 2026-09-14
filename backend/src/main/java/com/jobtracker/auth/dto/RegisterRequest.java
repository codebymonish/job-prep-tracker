package com.jobtracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
    @Email(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    @Size(max = 255)
    String email,

    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = ".*[A-Z].*", message = "must contain at least one uppercase letter")
    @Pattern(regexp = ".*\\d.*", message = "must contain at least one digit")
    String password,

    @NotBlank
    @Size(min = 1, max = 255)
    String name
) {}
