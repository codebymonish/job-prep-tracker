package com.jobtracker.application.dto;

public record ApplicationStatsResponse(
    long total,
    long applied,
    long interviewing,
    long offer,
    long rejected,
    long withdrawn
) {}
