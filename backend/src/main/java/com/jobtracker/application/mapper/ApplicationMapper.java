package com.jobtracker.application.mapper;

import com.jobtracker.application.dto.ApplicationDetailResponse;
import com.jobtracker.application.dto.ApplicationSummaryResponse;
import com.jobtracker.application.entity.Application;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApplicationMapper {
    ApplicationSummaryResponse toSummaryResponse(Application application);
    ApplicationDetailResponse toDetailResponse(Application application);
}
