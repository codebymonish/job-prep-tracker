package com.jobtracker.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateApplicationRequest(
    @NotBlank
    @Size(max = 255)
    String company,

    @NotBlank
    @Size(max = 255)
    String roleTitle,

    @Size(max = 20000)
    String jdText,

    @PastOrPresent
    LocalDate appliedDate
) {}
