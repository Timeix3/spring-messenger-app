package com.timeix3.messenger.mapper;

import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.dto.auth.RegisterRequest;
import com.timeix3.messenger.dto.user.UserResponse;
import com.timeix3.messenger.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  UserResponse toResponse(User user);

  @Mapping(target = "tokenType", source = "tokenType")
  @Mapping(target = "accessToken", source = "accessToken")
  @Mapping(target = "expiresInSeconds", source = "expiresInSeconds")
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "displayName", source = "user.displayName")
  AuthResponse toAuthResponse(
      User user, String tokenType, String accessToken, long expiresInSeconds);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "passwordHash", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @BeanMapping(ignoreUnmappedSourceProperties = "password")
  User toEntity(RegisterRequest request);
}
