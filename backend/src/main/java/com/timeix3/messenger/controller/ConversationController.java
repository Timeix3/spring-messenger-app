package com.timeix3.messenger.controller;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.conversation.ConversationListItemResponse;
import com.timeix3.messenger.dto.conversation.ConversationResponse;
import com.timeix3.messenger.dto.conversation.CreateConversationRequest;
import com.timeix3.messenger.dto.message.MarkReadRequest;
import com.timeix3.messenger.security.SecurityUtils;
import com.timeix3.messenger.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationService conversationService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ConversationResponse createConversation(
      @Valid @RequestBody CreateConversationRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return conversationService.createConversation(currentUserId, request);
  }

  @GetMapping
  public PageResponse<ConversationListItemResponse> getConversations(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return conversationService.getCurrentUserConversations(
        currentUserId, PageRequest.of(page, size));
  }

  @GetMapping("/{conversationId}")
  public ConversationResponse getConversation(@PathVariable Long conversationId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    return conversationService.getConversation(conversationId, currentUserId);
  }

  @PostMapping("/{conversationId}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(
      @PathVariable Long conversationId, @Valid @RequestBody MarkReadRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    conversationService.markRead(conversationId, currentUserId, request);
  }
}
