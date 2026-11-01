package com.timeix3.messenger.service;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.PresenceStatus;
import com.timeix3.messenger.dto.UserPresenceResponse;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.NotFoundException;
import com.timeix3.messenger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPresenceService {

    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public PageResponse<UserPresenceResponse> getUsersPresence(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);

        List<Long> userIds = users.getContent()
                .stream()
                .map(User::getId)
                .toList();

        Map<Long, PresenceStatus> statuses = presenceService.getStatuses(userIds);

        Page<UserPresenceResponse> result = users.map(user ->
                new UserPresenceResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        statuses.getOrDefault(user.getId(), PresenceStatus.OFFLINE)
                )
        );

        return PageResponse.from(result);
    }

    public UserPresenceResponse getUserPresence(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        PresenceStatus status = presenceService.getStatus(userId);

        return new UserPresenceResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                status
        );
    }
}
