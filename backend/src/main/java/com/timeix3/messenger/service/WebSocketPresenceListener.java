package com.timeix3.messenger.service;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

  private final PresenceService presenceService;

  @EventListener
  public void handleConnect(SessionConnectedEvent event) {
    try {
      StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

      String sessionId = accessor.getSessionId();
      Principal user = accessor.getUser();

      if (sessionId == null || user == null) {
        return;
      }

      Long userId = Long.valueOf(user.getName());

      presenceService.markOnline(userId, sessionId);
    } catch (Exception exception) {
      log.error("Failed to mark user online", exception);
    }
  }

  @EventListener
  public void handleDisconnect(SessionDisconnectEvent event) {
    try {
      StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

      String sessionId = accessor.getSessionId();

      if (sessionId == null) {
        sessionId = event.getSessionId();
      }

      if (sessionId == null) {
        return;
      }

      Long userId = presenceService.findUserIdBySession(sessionId);

      if (userId == null) {
        Principal user = accessor.getUser();

        if (user != null) {
          userId = Long.valueOf(user.getName());
        }
      }

      if (userId != null) {
        presenceService.markOffline(userId, sessionId);
      }
    } catch (Exception exception) {
      log.error("Failed to mark user offline", exception);
    }
  }
}
