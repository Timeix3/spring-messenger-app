package com.timeix3.messenger.service;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.user.UserResponse;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.NotFoundException;
import com.timeix3.messenger.mapper.UserMapper;
import com.timeix3.messenger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    public PageResponse<UserResponse> searchUsers(String query, Pageable pageable) {
        if (!StringUtils.hasText(query)) {
            return PageResponse.from(Page.empty(pageable));
        }
        Page<User> users = userRepository.searchUsers(query.trim(), pageable);
        Page<UserResponse> mapped = users.map(userMapper::toResponse);
        return PageResponse.from(mapped);
    }

    public PageResponse<UserResponse> getDirectoryUsers(Long currentUserId, Pageable pageable) {
        Page<User> users = userRepository.findAllPublicUsers(currentUserId, pageable);
        Page<UserResponse> mapped = users.map(userMapper::toResponse);
        return PageResponse.from(mapped);
    }
}
