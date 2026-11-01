package com.timeix3.messenger.controller;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.UserPresenceResponse;
import com.timeix3.messenger.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/statuses")
@RequiredArgsConstructor
public class UserPresenceController {

  private final UserPresenceService userPresenceService;

  @GetMapping
  public PageResponse<UserPresenceResponse> getUsersStatuses(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("username").ascending());

    return userPresenceService.getUsersPresence(pageable);
  }

  @GetMapping("/{userId}")
  public UserPresenceResponse getUserStatus(@PathVariable Long userId) {
    return userPresenceService.getUserPresence(userId);
  }
}
