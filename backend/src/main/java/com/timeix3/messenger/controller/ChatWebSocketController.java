package com.timeix3.messenger.controller;

import com.timeix3.messenger.dto.message.SendMessageRequest;
import com.timeix3.messenger.exception.UnauthorizedException;
import com.timeix3.messenger.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;

    @MessageMapping("/conversations/{conversationId}/messages")
    public void sendMessage(
            @DestinationVariable Long conversationId,
            @Payload @Valid SendMessageRequest request,
            Principal principal
    ) {
        Long currentUserId;
        try {
            currentUserId = Long.valueOf(principal.getName());
        } catch (Exception e) {
            throw new UnauthorizedException("Cannot resolve current user id");
        }
        messageService.sendMessage(conversationId, currentUserId, request);
    }
}
