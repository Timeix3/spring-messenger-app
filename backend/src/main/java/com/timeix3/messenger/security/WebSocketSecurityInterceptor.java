package com.timeix3.messenger.security;

import com.timeix3.messenger.exception.ForbiddenException;
import com.timeix3.messenger.exception.UnauthorizedException;
import com.timeix3.messenger.service.ConversationAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final String TOPIC_CONVERSATION_PREFIX = "/topic/conversations/";
    private static final String APP_CONVERSATION_PREFIX = "/app/conversations/";
    private static final String USER_DESTINATION_PREFIX = "/user";
    private static final String APP_PRESENCE_HEARTBEAT = "/app/presence/heartbeat";

    private final JwtWsTokenAuthenticator tokenAuthenticator;
    private final ConversationAccessChecker conversationAccessChecker;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        Authentication authentication = resolveAuthentication(accessor);

        if (authentication == null && StompCommand.CONNECT.equals(command)) {
            String token = extractTokenFromConnectHeaders(accessor);
            authentication = tokenAuthenticator.authenticate(token);

            accessor.setUser(authentication);

            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

            if (sessionAttributes != null) {
                sessionAttributes.put(
                        JwtHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE,
                        authentication
                );
            }
        }

        if (authentication == null) {
            throw new UnauthorizedException("WebSocket connection is not authenticated");
        }

        accessor.setUser(authentication);

        Long userId = SecurityUtils.getUserId(authentication);

        if (StompCommand.SUBSCRIBE.equals(command)) {
            validateSubscription(userId, accessor.getDestination());
        }

        if (StompCommand.SEND.equals(command)) {
            validateSend(userId, accessor.getDestination());
        }

        return message;
    }

    private Authentication resolveAuthentication(StompHeaderAccessor accessor) {
        Object user = accessor.getUser();

        if (user instanceof Authentication authentication) {
            return authentication;
        }

        if (user instanceof Long userId) {
            return UsernamePasswordAuthenticationToken.authenticated(
                    userId,
                    null,
                    List.of()
            );
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        if (sessionAttributes != null) {
            Object sessionAuthentication =
                    sessionAttributes.get(JwtHandshakeInterceptor.AUTHENTICATION_ATTRIBUTE);

            if (sessionAuthentication instanceof Authentication authentication) {
                return authentication;
            }
        }

        return null;
    }

    private String extractTokenFromConnectHeaders(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");

        if (StringUtils.hasText(authorizationHeader)
                && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return accessor.getFirstNativeHeader("token");
    }

    private void validateSubscription(Long userId, String destination) {
        if (!StringUtils.hasText(destination)) {
            throw new ForbiddenException("Subscription destination is required");
        }

        if (destination.startsWith(USER_DESTINATION_PREFIX)) {
            return;
        }

        if (destination.startsWith(TOPIC_CONVERSATION_PREFIX)) {
            String path = destination.substring(TOPIC_CONVERSATION_PREFIX.length());
            Long conversationId = parseConversationId(path);

            conversationAccessChecker.getConversationReference(conversationId, userId);
            return;
        }

        throw new ForbiddenException("Subscription is not allowed: " + destination);
    }

    private void validateSend(Long userId, String destination) {
        if (!StringUtils.hasText(destination)) {
            throw new ForbiddenException("Send destination is required");
        }

        if (destination.startsWith(APP_PRESENCE_HEARTBEAT))
            return;

        if (destination.startsWith(APP_CONVERSATION_PREFIX)) {
            String path = destination.substring(APP_CONVERSATION_PREFIX.length());
            Long conversationId = parseConversationId(path);

            conversationAccessChecker.getConversationReference(conversationId, userId);
            return;
        }

        throw new ForbiddenException("Sending messages to this destination is not allowed");
    }

    private Long parseConversationId(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ForbiddenException("Conversation id is missing");
        }

        String[] parts = path.split("/");

        if (parts.length == 0 || !StringUtils.hasText(parts[0])) {
            throw new ForbiddenException("Conversation id is missing");
        }

        try {
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException exception) {
            throw new ForbiddenException("Invalid conversation id");
        }
    }
}
