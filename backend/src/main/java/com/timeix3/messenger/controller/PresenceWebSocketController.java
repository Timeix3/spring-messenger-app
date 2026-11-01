package com.timeix3.messenger.controller;

import com.timeix3.messenger.service.PresenceService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PresenceWebSocketController {

  private final PresenceService presenceService;

  @MessageMapping("/presence/heartbeat")
  public void heartbeat(
      Principal principal,
      @Header(name = SimpMessageHeaderAccessor.SESSION_ID_HEADER, required = false)
          String sessionId) {
    Long userId = Long.valueOf(principal.getName());
    presenceService.heartbeat(userId, sessionId);
  }
}
