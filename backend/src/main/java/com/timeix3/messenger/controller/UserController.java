package com.timeix3.messenger.controller;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.user.UserResponse;
import com.timeix3.messenger.security.SecurityUtils;
import com.timeix3.messenger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return userService.getUserById(currentUserId);
    }

    @GetMapping("/{userId}")
    public UserResponse getUserById(@PathVariable Long userId) {
        return userService.getUserById(userId);
    }

    @GetMapping("/search")
    public PageResponse<UserResponse> searchUsers(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userService.searchUsers(query, PageRequest.of(page, size));
    }

    @GetMapping("/directory")
    public PageResponse<UserResponse> getDirectoryUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return userService.getDirectoryUsers(currentUserId, PageRequest.of(page, size));
    }
}
