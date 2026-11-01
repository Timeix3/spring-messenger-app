package com.timeix3.messenger.service;

import com.timeix3.messenger.exception.TooManyRequestsException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageRateLimiter {

  private static final long MESSAGE_LIMIT = 10;
  private static final long WINDOW_MILLIS = 5000;

  private static final String RATE_LIMIT_KEY = "ratelimit:messages:user:%d";

  private final RedisTemplate<String, String> redisTemplate;
  private final RedisScript<Long> messageRateLimitScript;

  public boolean isAllowed(Long userId) {
    if (userId == null) {
      return false;
    }

    String key = RATE_LIMIT_KEY.formatted(userId);

    Long result =
        redisTemplate.execute(
            messageRateLimitScript,
            List.of(key),
            String.valueOf(System.currentTimeMillis()),
            String.valueOf(WINDOW_MILLIS),
            String.valueOf(MESSAGE_LIMIT),
            UUID.randomUUID().toString());

    return result != null && result == 1L;
  }

  public void checkAllowed(Long userId) {
    if (!isAllowed(userId)) {
      throw new TooManyRequestsException("Too many messages. Please slow down.");
    }
  }
}
