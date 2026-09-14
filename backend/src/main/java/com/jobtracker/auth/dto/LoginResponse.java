package com.jobtracker.auth.dto;

public record LoginResponse(String accessToken, int expiresIn) {}
