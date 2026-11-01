package com.timeix3.messenger.controller;

import com.timeix3.messenger.dto.ErrorResponse;
import com.timeix3.messenger.dto.message.SendMessageRequest;
import com.timeix3.messenger.exception.TooManyRequestsException;
import com.timeix3.messenger.exception.UnauthorizedException;
import com.timeix3.messenger.service.MessageService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

  private final MessageService messageService;

  @MessageMapping("/conversations/{conversationId}/messages")
  public void sendMessage(
      @DestinationVariable Long conversationId,
      @Payload @Valid SendMessageRequest request,
      Principal principal) {
    Long currentUserId;
    try {
      currentUserId = Long.valueOf(principal.getName());
    } catch (Exception e) {
      throw new UnauthorizedException("Cannot resolve current user id");
    }
    messageService.sendMessage(conversationId, currentUserId, request);
  }

  @MessageExceptionHandler(TooManyRequestsException.class)
  @SendToUser("/queue/errors")
  public ErrorResponse handleTooManyRequests(TooManyRequestsException exception) {
    return new ErrorResponse(Instant.now(), exception.getMessage(), Map.of());
  }
}
