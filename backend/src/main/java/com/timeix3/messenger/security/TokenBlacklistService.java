package com.timeix3.messenger.security;

import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

  private static final String BLACKLIST_PREFIX = "auth:jwt:blacklist:";

  private final StringRedisTemplate stringRedisTemplate;

  public TokenBlacklistService(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  public void blacklist(Claims claims) {
    String jti = claims.getId();

    if (jti == null) {
      return;
    }

    Instant expiration = claims.getExpiration().toInstant();
    Duration ttl = Duration.between(Instant.now(), expiration);

    if (ttl.isPositive()) {
      stringRedisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "revoked", ttl);
    }
  }

  public boolean isBlacklisted(String jti) {
    if (jti == null) {
      return false;
    }

    return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + jti));
  }
}
