package com.timeix3.messenger.service;

import com.timeix3.messenger.dto.PresenceStatus;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PresenceService {

  private static final Duration ONLINE_TTL = Duration.ofMinutes(2);

  private static final String USER_STATUS_KEY = "user:%d:status";
  private static final String USER_SESSIONS_KEY = "user:%d:ws:sessions";
  private static final String WS_SESSION_KEY = "ws:session:%s";

  private final RedisTemplate<String, String> redisTemplate;

  public void markOnline(Long userId, String sessionId) {
    if (userId == null || sessionId == null) {
      return;
    }

    redisTemplate.opsForSet().add(userSessionsKey(userId), sessionId);
    redisTemplate.opsForValue().set(sessionKey(sessionId), userId.toString(), ONLINE_TTL);

    redisTemplate
        .opsForValue()
        .set(userStatusKey(userId), PresenceStatus.ONLINE.name(), ONLINE_TTL);
  }

  public void markOffline(Long userId, String sessionId) {
    if (userId == null || sessionId == null) {
      return;
    }

    redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
    redisTemplate.delete(sessionKey(sessionId));

    if (!hasActiveSessions(userId)) {
      redisTemplate.opsForValue().set(userStatusKey(userId), PresenceStatus.OFFLINE.name());

      redisTemplate.delete(userSessionsKey(userId));
    }
  }

  public void heartbeat(Long userId, String sessionId) {
    markOnline(userId, sessionId);
  }

  public PresenceStatus getStatus(Long userId) {
    String status = redisTemplate.opsForValue().get(userStatusKey(userId));

    if (PresenceStatus.ONLINE.name().equals(status)) {
      return PresenceStatus.ONLINE;
    }

    return PresenceStatus.OFFLINE;
  }

  public Map<Long, PresenceStatus> getStatuses(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Collections.emptyMap();
    }

    List<Long> orderedUserIds = userIds.stream().filter(Objects::nonNull).distinct().toList();

    if (orderedUserIds.isEmpty()) {
      return Collections.emptyMap();
    }

    List<String> keys = orderedUserIds.stream().map(this::userStatusKey).toList();

    List<String> values = redisTemplate.opsForValue().multiGet(keys);

    Map<Long, PresenceStatus> result = new HashMap<>();

    for (int i = 0; i < orderedUserIds.size(); i++) {
      Long userId = orderedUserIds.get(i);
      String value = values != null ? values.get(i) : null;

      PresenceStatus status =
          PresenceStatus.ONLINE.name().equals(value)
              ? PresenceStatus.ONLINE
              : PresenceStatus.OFFLINE;

      result.put(userId, status);
    }

    return result;
  }

  public Long findUserIdBySession(String sessionId) {
    if (sessionId == null) {
      return null;
    }

    String userId = redisTemplate.opsForValue().get(sessionKey(sessionId));

    if (userId == null) {
      return null;
    }

    try {
      return Long.parseLong(userId);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private boolean hasActiveSessions(Long userId) {
    Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));

    if (sessionIds == null || sessionIds.isEmpty()) {
      return false;
    }

    List<String> sessionIdList = new ArrayList<>(sessionIds);

    List<String> sessionKeys = sessionIdList.stream().map(this::sessionKey).toList();

    List<String> values = redisTemplate.opsForValue().multiGet(sessionKeys);

    boolean hasActiveSessions = false;

    for (int i = 0; i < sessionIdList.size(); i++) {
      String value = values != null ? values.get(i) : null;

      if (value != null) {
        hasActiveSessions = true;
      } else {
        redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionIdList.get(i));
      }
    }

    return hasActiveSessions;
  }

  private String userStatusKey(Long userId) {
    return USER_STATUS_KEY.formatted(userId);
  }

  private String userSessionsKey(Long userId) {
    return USER_SESSIONS_KEY.formatted(userId);
  }

  private String sessionKey(String sessionId) {
    return WS_SESSION_KEY.formatted(sessionId);
  }
}
