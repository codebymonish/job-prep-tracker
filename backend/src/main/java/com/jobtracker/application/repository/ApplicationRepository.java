package com.jobtracker.application.repository;

import com.jobtracker.application.entity.Application;
import com.jobtracker.application.entity.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Page<Application> findByUserIdAndIsDeletedFalse(UUID userId, Pageable pageable);

    Page<Application> findByUserIdAndStatusAndIsDeletedFalse(UUID userId, ApplicationStatus status, Pageable pageable);

    Optional<Application> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    long countByUserIdAndIsDeletedFalse(UUID userId);

    long countByUserIdAndStatusAndIsDeletedFalse(UUID userId, ApplicationStatus status);
}
