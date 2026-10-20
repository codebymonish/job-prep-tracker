package com.jobtracker.application.controller;

import com.jobtracker.application.dto.*;
import com.jobtracker.application.entity.ApplicationStatus;
import com.jobtracker.application.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<Page<ApplicationSummaryResponse>> listApplications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        UUID userId = extractUserId(authentication);
        Page<ApplicationSummaryResponse> result = applicationService.listApplications(
                userId, page, size, status, sortBy, sortDir);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<ApplicationStatsResponse> getStats(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(applicationService.getApplicationStats(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDetailResponse> getApplication(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(applicationService.getApplication(userId, id));
    }

    @PostMapping
    public ResponseEntity<ApplicationDetailResponse> createApplication(
            Authentication authentication,
            @Valid @RequestBody CreateApplicationRequest request) {
        UUID userId = extractUserId(authentication);
        ApplicationDetailResponse created = applicationService.createApplication(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationDetailResponse> updateStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        UUID userId = extractUserId(authentication);
        ApplicationDetailResponse updated = applicationService.updateStatus(userId, id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(
            Authentication authentication,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authentication);
        applicationService.deleteApplication(userId, id);
        return ResponseEntity.noContent().build();
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return UUID.fromString(jwtAuth.getToken().getSubject());
        }
        throw new IllegalStateException("Unexpected authentication type");
    }
}
