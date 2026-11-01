package com.timeix3.messenger.security;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

  public static final String AUTHENTICATION_ATTRIBUTE = "WS_AUTHENTICATION";

  private final JwtWsTokenAuthenticator tokenAuthenticator;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String token = extractToken(request);

    if (!StringUtils.hasText(token)) {
      return true;
    }

    try {
      Authentication authentication = tokenAuthenticator.authenticate(token);
      attributes.put(AUTHENTICATION_ATTRIBUTE, authentication);
      return true;
    } catch (RuntimeException exception) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}

  private String extractToken(ServerHttpRequest request) {
    String authorizationHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
      return authorizationHeader.substring(7);
    }

    return UriComponentsBuilder.fromUri(request.getURI())
        .build()
        .getQueryParams()
        .getFirst("token");
  }
}
