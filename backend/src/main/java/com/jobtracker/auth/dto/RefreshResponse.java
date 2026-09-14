package com.jobtracker.auth.dto;

public record RefreshResponse(String accessToken, int expiresIn) {}
