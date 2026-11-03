package com.timeix3.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.ForbiddenException;
import com.timeix3.messenger.exception.TooManyRequestsException;
import com.timeix3.messenger.exception.UnauthorizedException;
import com.timeix3.messenger.security.CustomUserDetailsService;
import com.timeix3.messenger.security.JwtHandshakeInterceptor;
import com.timeix3.messenger.security.JwtService;
import com.timeix3.messenger.security.JwtWsTokenAuthenticator;
import com.timeix3.messenger.security.SecurityUtils;
import com.timeix3.messenger.security.TokenBlacklistService;
import com.timeix3.messenger.security.WebSocketSecurityInterceptor;
import com.timeix3.messenger.service.ConversationAccessChecker;
import com.timeix3.messenger.service.MessageRateLimiter;
import io.jsonwebtoken.Claims;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class AuthorizationIntegrationTests extends IntegrationTestSupport {

  private static final WebSocketHandler WEB_SOCKET_HANDLER = new TextWebSocketHandler();

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private CustomUserDetailsService userDetailsService;

  @Autowired private JwtService jwtService;

  @Autowired private TokenBlacklistService tokenBlacklistService;

  @Autowired private JwtHandshakeInterceptor handshakeInterceptor;

  @Autowired private JwtWsTokenAuthenticator wsTokenAuthenticator;

  @Autowired private WebSocketSecurityInterceptor webSocketSecurityInterceptor;

  @Autowired private ConversationAccessChecker conversationAccessChecker;

  @Autowired private MessageRateLimiter messageRateLimiter;

  @Test
  void registrationPersistsUserAndReturnsUsableJwt() throws Exception {
    String username = uniqueUsername();
    HttpResponse<String> response = register(username.toUpperCase(), "  Display Name  ");

    assertEquals(HttpStatus.CREATED.value(), response.statusCode());
    AuthResponse authResponse = objectMapper.readValue(response.body(), AuthResponse.class);
    assertEquals(username, authResponse.username());
    assertEquals("Display Name", authResponse.displayName());
    assertEquals("Bearer", authResponse.tokenType());
    assertEquals(900, authResponse.expiresInSeconds());

    Claims claims = jwtService.parseClaims(authResponse.accessToken());
    assertEquals(authResponse.userId().toString(), claims.getSubject());
    assertEquals(username, claims.get("username", String.class));
    assertTrue(jwtService.isTokenValid(authResponse.accessToken()));

    User user = userRepository.findById(authResponse.userId()).orElseThrow();
    assertEquals(username, user.getUsername());
    assertEquals("Display Name", user.getDisplayName());
    assertNotNull(user.getCreatedAt());

    assertEquals(
        username,
        jdbcTemplate.queryForObject(
            "select username from users where id = ?", String.class, authResponse.userId()));
    assertEquals(
        "Display Name",
        jdbcTemplate.queryForObject(
            "select display_name from users where id = ?", String.class, authResponse.userId()));
    String persistedPasswordHash =
        jdbcTemplate.queryForObject(
            "select password_hash from users where id = ?", String.class, authResponse.userId());
    assertNotNull(persistedPasswordHash);
    assertTrue(passwordEncoder.matches("Correct-Horse-123", persistedPasswordHash));
    assertFalse(persistedPasswordHash.equals("Correct-Horse-123"));

    UserDetails details = userDetailsService.loadUserByUsername(username.toUpperCase());
    assertEquals(username, details.getUsername());
    assertEquals(user.getPasswordHash(), details.getPassword());
  }

  @Test
  void loginAuthenticatesAndFilterProtectsCurrentUserEndpoint() throws Exception {
    String username = uniqueUsername();
    AuthResponse registered = authResponse(register(username, null));

    HttpResponse<String> login =
        post(
            "/api/auth/login",
            Map.of("username", username.toUpperCase(), "password", "Correct-Horse-123"),
            null);
    assertEquals(HttpStatus.OK.value(), login.statusCode());

    AuthResponse loggedIn = objectMapper.readValue(login.body(), AuthResponse.class);
    assertEquals(registered.userId(), loggedIn.userId());

    HttpResponse<String> currentUser = get("/api/users/me", loggedIn.accessToken());
    assertEquals(HttpStatus.OK.value(), currentUser.statusCode());
    assertEquals(username, objectMapper.readTree(currentUser.body()).get("username").asText());

    HttpResponse<String> unauthorized = get("/api/users/me", null);
    assertEquals(HttpStatus.UNAUTHORIZED.value(), unauthorized.statusCode());

    HttpResponse<String> invalidLogin =
        post("/api/auth/login", Map.of("username", username, "password", "wrong-password"), null);
    assertEquals(HttpStatus.UNAUTHORIZED.value(), invalidLogin.statusCode());
  }

  @Test
  void openApiDocumentsBearerAuthenticationForProtectedEndpoints() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs", null);

    assertEquals(HttpStatus.OK.value(), response.statusCode());

    var apiDocs = objectMapper.readTree(response.body());
    var bearerScheme = apiDocs.at("/components/securitySchemes/bearerAuth");
    assertEquals("http", bearerScheme.get("type").asText());
    assertEquals("bearer", bearerScheme.get("scheme").asText());
    assertEquals("JWT", bearerScheme.get("bearerFormat").asText());
    assertTrue(apiDocs.at("/security/0/bearerAuth").isArray());
    assertEquals(0, apiDocs.at("/paths/~1api~1auth~1register/post/security").size());
    assertEquals(0, apiDocs.at("/paths/~1api~1auth~1login/post/security").size());
  }

  @Test
  void logoutRevokesJwtInRedisAndFilterRejectsIt() throws Exception {
    AuthResponse authResponse = authResponse(register(uniqueUsername(), null));
    Claims claims = jwtService.parseClaims(authResponse.accessToken());

    HttpResponse<String> logout = post("/api/auth/logout", Map.of(), authResponse.accessToken());
    assertEquals(HttpStatus.NO_CONTENT.value(), logout.statusCode());
    assertTrue(tokenBlacklistService.isBlacklisted(claims.getId()));

    HttpResponse<String> rejected = get("/api/users/me", authResponse.accessToken());
    assertEquals(HttpStatus.UNAUTHORIZED.value(), rejected.statusCode());
  }

  @Test
  void registrationRejectsDuplicatesAndInvalidRequests() throws Exception {
    String username = uniqueUsername();
    assertEquals(HttpStatus.CREATED.value(), register(username, null).statusCode());
    assertEquals(HttpStatus.CONFLICT.value(), register(username.toUpperCase(), null).statusCode());

    HttpResponse<String> invalid =
        post("/api/auth/register", Map.of("username", "a", "password", "short"), null);
    assertEquals(HttpStatus.BAD_REQUEST.value(), invalid.statusCode());
  }

  @Test
  void messageRateLimiterAllowsTenMessagesAndRejectsTheEleventhPerUser()
      throws InterruptedException {
    long userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    long otherUserId = userId == Long.MAX_VALUE ? userId - 1 : userId + 1;

    for (int message = 0; message < 10; message++) {
      assertTrue(messageRateLimiter.isAllowed(userId));
    }

    assertThrows(TooManyRequestsException.class, () -> messageRateLimiter.checkAllowed(userId));
    assertTrue(messageRateLimiter.isAllowed(otherUserId));
    assertFalse(messageRateLimiter.isAllowed(null));

    Thread.sleep(5_200);
    assertTrue(messageRateLimiter.isAllowed(userId));
  }

  @Test
  void websocketHandshakeAndStompMessagesUseJwtAndEnforceConversationAccess() throws Exception {
    String username = uniqueUsername();
    AuthResponse member = authResponse(register(username, null));
    AuthResponse other = authResponse(register(uniqueUsername(), null));
    AuthResponse outsider = authResponse(register(uniqueUsername(), null));

    Authentication wsAuthentication = wsTokenAuthenticator.authenticate(member.accessToken());
    assertEquals(member.userId(), SecurityUtils.getUserId(wsAuthentication));
    assertThrows(UnauthorizedException.class, () -> wsTokenAuthenticator.authenticate("invalid"));

    Map<String, Object> handshakeAttributes = new HashMap<>();
    ServerHttpRequest handshakeRequest = handshakeRequest(member.accessToken());
    ServerHttpResponse handshakeResponse = mock(ServerHttpResponse.class);

    assertTrue(
        handshakeInterceptor.beforeHandshake(
            handshakeRequest, handshakeResponse, WEB_SOCKET_HANDLER, handshakeAttributes));
    Authentication handshakeAuthentication =
        (Authentication) handshakeAttributes.get(JwtHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE);
    assertEquals(member.userId(), SecurityUtils.getUserId(handshakeAuthentication));

    ServerHttpRequest invalidHandshake = handshakeRequest("invalid");
    ServerHttpResponse rejectedHandshake = mock(ServerHttpResponse.class);
    assertFalse(
        handshakeInterceptor.beforeHandshake(
            invalidHandshake, rejectedHandshake, WEB_SOCKET_HANDLER, new HashMap<>()));
    verify(rejectedHandshake).setStatusCode(HttpStatus.UNAUTHORIZED);

    Message<?> connect =
        stompMessage(
            StompCommand.CONNECT,
            accessor -> {
              accessor.setNativeHeader("Authorization", "Bearer " + member.accessToken());
              accessor.setSessionAttributes(new HashMap<>());
            });
    Message<?> authenticatedConnect = webSocketSecurityInterceptor.preSend(connect, channel());
    Authentication connectAuthentication =
        (Authentication)
            StompHeaderAccessor.getAccessor(authenticatedConnect, StompHeaderAccessor.class)
                .getUser();
    assertEquals(member.userId(), SecurityUtils.getUserId(connectAuthentication));

    Long conversationId = createConversation(member.userId(), other.userId());
    Message<?> subscribeToConversation =
        stompMessage(
            StompCommand.SUBSCRIBE,
            accessor -> {
              accessor.setUser(wsAuthentication);
              accessor.setDestination("/topic/conversations/" + conversationId);
            });
    assertNotNull(webSocketSecurityInterceptor.preSend(subscribeToConversation, channel()));
    assertNotNull(
        conversationAccessChecker.getConversationReference(conversationId, member.userId()));

    Message<?> deniedSubscription =
        stompMessage(
            StompCommand.SUBSCRIBE,
            accessor -> {
              accessor.setUser(wsTokenAuthenticator.authenticate(outsider.accessToken()));
              accessor.setDestination("/topic/conversations/" + conversationId);
            });
    assertThrows(
        ForbiddenException.class,
        () -> webSocketSecurityInterceptor.preSend(deniedSubscription, channel()));

    Message<?> heartbeat =
        stompMessage(
            StompCommand.SEND,
            accessor -> {
              accessor.setUser(wsAuthentication);
              accessor.setDestination("/app/presence/heartbeat");
            });
    assertNotNull(webSocketSecurityInterceptor.preSend(heartbeat, channel()));

    Message<?> unauthenticated =
        stompMessage(
            StompCommand.SUBSCRIBE, accessor -> accessor.setDestination("/user/queue/messages"));
    assertThrows(
        UnauthorizedException.class,
        () -> webSocketSecurityInterceptor.preSend(unauthenticated, channel()));
  }

  private Message<?> stompMessage(StompCommand command, Consumer<StompHeaderAccessor> configure) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    configure.accept(accessor);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private MessageChannel channel() {
    return (message, timeout) -> true;
  }

  private ServerHttpRequest handshakeRequest(String token) {
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getURI()).thenReturn(URI.create("http://localhost/ws"));
    return request;
  }
}
