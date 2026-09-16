package com.jobtracker.auth.mapper;

import com.jobtracker.auth.dto.UserDto;
import com.jobtracker.auth.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDto toDto(User user);
}
