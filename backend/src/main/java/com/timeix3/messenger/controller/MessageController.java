package com.timeix3.messenger.controller;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.message.MessageResponse;
import com.timeix3.messenger.dto.message.SendMessageRequest;
import com.timeix3.messenger.security.SecurityUtils;
import com.timeix3.messenger.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class MessageController {

  private final MessageService messageService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MessageResponse sendMessage(
      @PathVariable Long conversationId, @Valid @RequestBody SendMessageRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return messageService.sendMessage(conversationId, currentUserId, request);
  }

  @GetMapping
  public PageResponse<MessageResponse> getMessages(
      @PathVariable Long conversationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return messageService.getMessages(conversationId, currentUserId, PageRequest.of(page, size));
  }
}
