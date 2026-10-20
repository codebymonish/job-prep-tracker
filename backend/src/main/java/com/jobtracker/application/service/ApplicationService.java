package com.jobtracker.application.service;

import com.jobtracker.application.dto.*;
import com.jobtracker.application.entity.Application;
import com.jobtracker.application.entity.ApplicationStatus;
import com.jobtracker.application.exception.ApplicationNotFoundException;
import com.jobtracker.application.exception.StaleApplicationException;
import com.jobtracker.application.mapper.ApplicationMapper;
import com.jobtracker.application.repository.ApplicationRepository;
import com.jobtracker.auth.repository.UserRepository;
import com.jobtracker.common.entity.AuditLog;
import com.jobtracker.common.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class ApplicationService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "appliedDate", "company", "status");

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationStatusTransitionValidator validator;
    private final ApplicationMapper applicationMapper;

    public ApplicationService(ApplicationRepository applicationRepository,
                              UserRepository userRepository,
                              AuditLogRepository auditLogRepository,
                              ApplicationStatusTransitionValidator validator,
                              ApplicationMapper applicationMapper) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.validator = validator;
        this.applicationMapper = applicationMapper;
    }

    @Transactional(readOnly = true)
    public Page<ApplicationSummaryResponse> listApplications(
            UUID userId, int page, int size, ApplicationStatus statusFilter, String sortBy, String sortDir) {
        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Application> applications;
        if (statusFilter != null) {
            applications = applicationRepository.findByUserIdAndStatusAndIsDeletedFalse(userId, statusFilter, pageable);
        } else {
            applications = applicationRepository.findByUserIdAndIsDeletedFalse(userId, pageable);
        }

        return applications.map(applicationMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationSummaryResponse> listApplications(
            UUID userId, int page, int size, ApplicationStatus statusFilter) {
        return listApplications(userId, page, size, statusFilter, "createdAt", "DESC");
    }

    @Transactional(readOnly = true)
    public ApplicationDetailResponse getApplication(UUID userId, UUID id) {
        Application application = applicationRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)
                .orElseThrow(ApplicationNotFoundException::new);
        return applicationMapper.toDetailResponse(application);
    }

    @Transactional
    public ApplicationDetailResponse createApplication(UUID userId, CreateApplicationRequest request) {
        Application application = new Application();
        application.setUser(userRepository.getReferenceById(userId));
        application.setCompany(request.company());
        application.setRoleTitle(request.roleTitle());
        application.setJdText(request.jdText());
        application.setAppliedDate(request.appliedDate() != null ? request.appliedDate() : LocalDate.now());
        application.setStatus(ApplicationStatus.APPLIED);
        application.setDeleted(false);

        // repo.save must precede audit log write so entity_id is populated from saved application id
        Application saved = applicationRepository.saveAndFlush(application);

        AuditLog auditLog = new AuditLog(
                userId,
                "APPLICATION",
                saved.getId(),
                "CREATE",
                null,
                null
        );
        auditLogRepository.save(auditLog);

        return applicationMapper.toDetailResponse(saved);
    }

    @Transactional
    public ApplicationDetailResponse updateStatus(UUID userId, UUID id, UpdateApplicationStatusRequest request) {
        Application application = applicationRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)
                .orElseThrow(ApplicationNotFoundException::new);

        if (request.version() != application.getVersion()) {
            throw new StaleApplicationException();
        }

        validator.validate(application.getStatus(), request.status());

        ApplicationStatus oldStatus = application.getStatus();
        application.setStatus(request.status());

        Application updated = applicationRepository.saveAndFlush(application);

        String beforeJson = "{\"status\":\"" + oldStatus.name() + "\"}";
        String afterJson = "{\"status\":\"" + request.status().name() + "\"}";
        AuditLog auditLog = new AuditLog(
                userId,
                "APPLICATION",
                id,
                "STATUS_CHANGE",
                beforeJson,
                afterJson
        );
        auditLogRepository.save(auditLog);

        return applicationMapper.toDetailResponse(updated);
    }

    @Transactional
    public void deleteApplication(UUID userId, UUID id) {
        Application application = applicationRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)
                .orElseThrow(ApplicationNotFoundException::new);

        application.setDeleted(true);
        applicationRepository.saveAndFlush(application);

        AuditLog auditLog = new AuditLog(
                userId,
                "APPLICATION",
                id,
                "DELETE",
                null,
                null
        );
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public ApplicationStatsResponse getApplicationStats(UUID userId) {
        long total        = applicationRepository.countByUserIdAndIsDeletedFalse(userId);
        long applied      = applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, ApplicationStatus.APPLIED);
        long interviewing = applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, ApplicationStatus.INTERVIEWING);
        long offer        = applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, ApplicationStatus.OFFER);
        long rejected     = applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, ApplicationStatus.REJECTED);
        long withdrawn    = applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, ApplicationStatus.WITHDRAWN);
        return new ApplicationStatsResponse(total, applied, interviewing, offer, rejected, withdrawn);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String property = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        if (!ALLOWED_SORT_FIELDS.contains(property)) {
            throw new IllegalArgumentException("Invalid sort property: " + property);
        }

        Sort.Direction direction = (sortDir != null && sortDir.equalsIgnoreCase("ASC"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, property);
    }
}
