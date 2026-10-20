package com.jobtracker.application.service;

import com.jobtracker.application.dto.*;
import com.jobtracker.application.entity.Application;
import com.jobtracker.application.entity.ApplicationStatus;
import com.jobtracker.application.exception.ApplicationNotFoundException;
import com.jobtracker.application.exception.StaleApplicationException;
import com.jobtracker.application.mapper.ApplicationMapper;
import com.jobtracker.application.repository.ApplicationRepository;
import com.jobtracker.auth.entity.User;
import com.jobtracker.auth.repository.UserRepository;
import com.jobtracker.common.entity.AuditLog;
import com.jobtracker.common.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ApplicationStatusTransitionValidator validator;

    @Mock
    private ApplicationMapper applicationMapper;

    @InjectMocks
    private ApplicationService applicationService;

    private UUID userId;
    private UUID appId;
    private Application sampleApp;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        appId = UUID.randomUUID();

        sampleUser = new User();
        sampleUser.setId(userId);

        sampleApp = new Application();
        sampleApp.setId(appId);
        sampleApp.setUser(sampleUser);
        sampleApp.setCompany("Acme Corp");
        sampleApp.setRoleTitle("Software Engineer");
        sampleApp.setJdText("Exciting role");
        sampleApp.setStatus(ApplicationStatus.APPLIED);
        sampleApp.setAppliedDate(LocalDate.now());
        sampleApp.setVersion(0);
        sampleApp.setDeleted(false);
    }

    @Test
    void testListApplicationsWithoutFilter() {
        Page<Application> page = new PageImpl<>(List.of(sampleApp));
        when(applicationRepository.findByUserIdAndIsDeletedFalse(eq(userId), any(Pageable.class))).thenReturn(page);
        ApplicationSummaryResponse summary = new ApplicationSummaryResponse(
                appId, "Acme Corp", "Software Engineer", ApplicationStatus.APPLIED, LocalDate.now(), 0, Instant.now());
        when(applicationMapper.toSummaryResponse(sampleApp)).thenReturn(summary);

        Page<ApplicationSummaryResponse> result = applicationService.listApplications(userId, 0, 20, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).company()).isEqualTo("Acme Corp");
        verify(applicationRepository).findByUserIdAndIsDeletedFalse(eq(userId), any(Pageable.class));
        verify(applicationRepository, never()).findByUserIdAndStatusAndIsDeletedFalse(any(), any(), any());
    }

    @Test
    void testListApplicationsWithStatusFilter() {
        Page<Application> page = new PageImpl<>(List.of(sampleApp));
        when(applicationRepository.findByUserIdAndStatusAndIsDeletedFalse(eq(userId), eq(ApplicationStatus.APPLIED), any(Pageable.class)))
                .thenReturn(page);
        ApplicationSummaryResponse summary = new ApplicationSummaryResponse(
                appId, "Acme Corp", "Software Engineer", ApplicationStatus.APPLIED, LocalDate.now(), 0, Instant.now());
        when(applicationMapper.toSummaryResponse(sampleApp)).thenReturn(summary);

        Page<ApplicationSummaryResponse> result = applicationService.listApplications(userId, 0, 20, ApplicationStatus.APPLIED);

        assertThat(result.getContent()).hasSize(1);
        verify(applicationRepository).findByUserIdAndStatusAndIsDeletedFalse(eq(userId), eq(ApplicationStatus.APPLIED), any(Pageable.class));
        verify(applicationRepository, never()).findByUserIdAndIsDeletedFalse(any(), any());
    }

    @Test
    void testListApplicationsWithCustomSort() {
        Page<Application> page = new PageImpl<>(List.of(sampleApp));
        when(applicationRepository.findByUserIdAndIsDeletedFalse(eq(userId), any(Pageable.class))).thenReturn(page);
        ApplicationSummaryResponse summary = new ApplicationSummaryResponse(
                appId, "Acme Corp", "Software Engineer", ApplicationStatus.APPLIED, LocalDate.now(), 0, Instant.now());
        when(applicationMapper.toSummaryResponse(sampleApp)).thenReturn(summary);

        Page<ApplicationSummaryResponse> result = applicationService.listApplications(
                userId, 0, 10, null, "company", "ASC");

        assertThat(result).isNotNull();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(applicationRepository).findByUserIdAndIsDeletedFalse(eq(userId), pageableCaptor.capture());
        Sort sort = pageableCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("company")).isNotNull();
        assertThat(sort.getOrderFor("company").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void testListApplicationsInvalidSortThrows() {
        assertThatThrownBy(() -> applicationService.listApplications(userId, 0, 10, null, "invalidField", "ASC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort property");
    }

    @Test
    void testGetApplicationFound() {
        when(applicationRepository.findByIdAndUserIdAndIsDeletedFalse(appId, userId)).thenReturn(Optional.of(sampleApp));
        ApplicationDetailResponse detail = new ApplicationDetailResponse(
                appId, "Acme Corp", "Software Engineer", "Exciting role", ApplicationStatus.APPLIED, LocalDate.now(), 0, Instant.now(), Instant.now());
        when(applicationMapper.toDetailResponse(sampleApp)).thenReturn(detail);

        ApplicationDetailResponse result = applicationService.getApplication(userId, appId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(appId);
        assertThat(result.company()).isEqualTo("Acme Corp");
    }

    @Test
    void testGetApplicationNotFound() {
        when(applicationRepository.findByIdAndUserIdAndIsDeletedFalse(appId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplication(userId, appId))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void testCreateApplicationWithAppliedDate() {
        LocalDate customDate = LocalDate.now().minusDays(2);
        CreateApplicationRequest request = new CreateApplicationRequest("Tech Corp", "Backend Dev", "Spring Boot", customDate);
        when(userRepository.getReferenceById(userId)).thenReturn(sampleUser);

        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> {
            Application a = invocation.getArgument(0);
            a.setId(appId);
            return a;
        });

        ApplicationDetailResponse detail = new ApplicationDetailResponse(
                appId, "Tech Corp", "Backend Dev", "Spring Boot", ApplicationStatus.APPLIED, customDate, 0, Instant.now(), Instant.now());
        when(applicationMapper.toDetailResponse(any(Application.class))).thenReturn(detail);

        ApplicationDetailResponse response = applicationService.createApplication(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.company()).isEqualTo("Tech Corp");

        ArgumentCaptor<Application> appCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).saveAndFlush(appCaptor.capture());
        assertThat(appCaptor.getValue().getAppliedDate()).isEqualTo(customDate);
        assertThat(appCaptor.getValue().getStatus()).isEqualTo(ApplicationStatus.APPLIED);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getUserId()).isEqualTo(userId);
        assertThat(audit.getEntityType()).isEqualTo("APPLICATION");
        assertThat(audit.getEntityId()).isEqualTo(appId);
        assertThat(audit.getAction()).isEqualTo("CREATE");
    }

    @Test
    void testCreateApplicationWithNullAppliedDateDefaultsToToday() {
        CreateApplicationRequest request = new CreateApplicationRequest("Tech Corp", "Backend Dev", null, null);
        when(userRepository.getReferenceById(userId)).thenReturn(sampleUser);

        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> {
            Application a = invocation.getArgument(0);
            a.setId(appId);
            return a;
        });

        ApplicationDetailResponse detail = new ApplicationDetailResponse(
                appId, "Tech Corp", "Backend Dev", null, ApplicationStatus.APPLIED, LocalDate.now(), 0, Instant.now(), Instant.now());
        when(applicationMapper.toDetailResponse(any(Application.class))).thenReturn(detail);

        applicationService.createApplication(userId, request);

        ArgumentCaptor<Application> appCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).saveAndFlush(appCaptor.capture());
        assertThat(appCaptor.getValue().getAppliedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void testUpdateStatusSuccess() {
        when(applicationRepository.findByIdAndUserIdAndIsDeletedFalse(appId, userId)).thenReturn(Optional.of(sampleApp));
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEWING, 0);

        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationDetailResponse detail = new ApplicationDetailResponse(
                appId, "Acme Corp", "Software Engineer", "Exciting role", ApplicationStatus.INTERVIEWING, LocalDate.now(), 1, Instant.now(), Instant.now());
        when(applicationMapper.toDetailResponse(any(Application.class))).thenReturn(detail);

        ApplicationDetailResponse result = applicationService.updateStatus(userId, appId, request);

        assertThat(result.status()).isEqualTo(ApplicationStatus.INTERVIEWING);
        verify(validator).validate(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEWING);
        verify(applicationRepository).saveAndFlush(sampleApp);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo("STATUS_CHANGE");
        assertThat(audit.getEntityId()).isEqualTo(appId);
        assertThat(audit.getBeforeJson()).contains("APPLIED");
        assertThat(audit.getAfterJson()).contains("INTERVIEWING");
    }

    @Test
    void testUpdateStatusStaleVersionThrows() {
        sampleApp.setVersion(1); // current version is 1
        when(applicationRepository.findByIdAndUserIdAndIsDeletedFalse(appId, userId)).thenReturn(Optional.of(sampleApp));
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEWING, 0); // client passes stale 0

        assertThatThrownBy(() -> applicationService.updateStatus(userId, appId, request))
                .isInstanceOf(StaleApplicationException.class);

        verify(validator, never()).validate(any(), any());
        verify(applicationRepository, never()).saveAndFlush(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void testUpdateStatusNotFoundThrows() {
        when(applicationRepository.findByIdAndUserIdAndIsDeletedFalse(appId, userId)).thenReturn(Optional.empty());
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEWING, 0);

        assertThatThrownBy(() -> applicationService.updateStatus(userId, appId, request))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void testDeleteApplicationSuccess() {
        when(applicationRepository.findByIdAndUserIdAndIsDeletedFalse(appId, userId)).thenReturn(Optional.of(sampleApp));

        applicationService.deleteApplication(userId, appId);

        assertThat(sampleApp.isDeleted()).isTrue();
        verify(applicationRepository).saveAndFlush(sampleApp);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getAction()).isEqualTo("DELETE");
        assertThat(audit.getEntityId()).isEqualTo(appId);
    }

    @Test
    void testDeleteApplicationNotFoundThrows() {
        when(applicationRepository.findByIdAndUserIdAndIsDeletedFalse(appId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.deleteApplication(userId, appId))
                .isInstanceOf(ApplicationNotFoundException.class);

        verify(applicationRepository, never()).saveAndFlush(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void testGetApplicationStats() {
        when(applicationRepository.countByUserIdAndIsDeletedFalse(userId)).thenReturn(5L);
        when(applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, com.jobtracker.application.entity.ApplicationStatus.APPLIED)).thenReturn(2L);
        when(applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, com.jobtracker.application.entity.ApplicationStatus.INTERVIEWING)).thenReturn(1L);
        when(applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, com.jobtracker.application.entity.ApplicationStatus.OFFER)).thenReturn(1L);
        when(applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, com.jobtracker.application.entity.ApplicationStatus.REJECTED)).thenReturn(1L);
        when(applicationRepository.countByUserIdAndStatusAndIsDeletedFalse(userId, com.jobtracker.application.entity.ApplicationStatus.WITHDRAWN)).thenReturn(0L);

        com.jobtracker.application.dto.ApplicationStatsResponse stats = applicationService.getApplicationStats(userId);

        assertThat(stats.total()).isEqualTo(5L);
        assertThat(stats.applied()).isEqualTo(2L);
        assertThat(stats.interviewing()).isEqualTo(1L);
        assertThat(stats.offer()).isEqualTo(1L);
        assertThat(stats.rejected()).isEqualTo(1L);
        assertThat(stats.withdrawn()).isEqualTo(0L);
    }
}
