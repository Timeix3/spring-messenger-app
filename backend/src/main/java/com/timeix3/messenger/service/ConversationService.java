package com.timeix3.messenger.service;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.conversation.ConversationListItemResponse;
import com.timeix3.messenger.dto.conversation.ConversationResponse;
import com.timeix3.messenger.dto.conversation.CreateConversationRequest;
import com.timeix3.messenger.dto.message.MarkReadRequest;
import com.timeix3.messenger.entity.Conversation;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.BadRequestException;
import com.timeix3.messenger.exception.NotFoundException;
import com.timeix3.messenger.mapper.ConversationMapper;
import com.timeix3.messenger.mapper.MessageMapper;
import com.timeix3.messenger.mapper.UserMapper;
import com.timeix3.messenger.repository.ConversationRepository;
import com.timeix3.messenger.repository.MessageRepository;
import com.timeix3.messenger.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final UserRepository userRepository;
  private final MessageRepository messageRepository;

  private final ConversationMapper conversationMapper;
  private final UserMapper userMapper;
  private final MessageMapper messageMapper;

  private final ConversationAccessChecker accessChecker;

  @Transactional
  public ConversationResponse createConversation(
      Long currentUserId, CreateConversationRequest request) {
    Long companionId = request.companionId();

    if (currentUserId.equals(companionId)) {
      throw new BadRequestException("Cannot create conversation with yourself");
    }

    User companion =
        userRepository
            .findById(companionId)
            .orElseThrow(() -> new NotFoundException("Companion user not found"));

    Optional<Conversation> existingConversation =
        conversationRepository.findBetweenUsers(currentUserId, companion.getId());

    if (existingConversation.isPresent()) {
      return getConversation(existingConversation.get().getId(), currentUserId);
    }

    Long user1Id = Math.min(currentUserId, companion.getId());
    Long user2Id = Math.max(currentUserId, companion.getId());

    Conversation conversation = new Conversation();
    conversation.setUser1(userRepository.getReferenceById(user1Id));
    conversation.setUser2(userRepository.getReferenceById(user2Id));

    Conversation savedConversation = conversationRepository.save(conversation);

    return getConversation(savedConversation.getId(), currentUserId);
  }

  public PageResponse<ConversationListItemResponse> getCurrentUserConversations(
      Long currentUserId, Pageable pageable) {
    Page<Conversation> conversations =
        conversationRepository.findAllByUserId(currentUserId, pageable);

    Page<ConversationListItemResponse> items =
        conversations.map(
            conversation -> {
              return new ConversationListItemResponse(
                  conversation.getId(),
                  userMapper.toResponse(
                      accessChecker.resolveOtherUser(conversation, currentUserId)),
                  messageMapper.toResponse(conversation.getLastMessage()),
                  conversation.getLastMessageAt(),
                  conversationRepository.countUnreadMessages(conversation.getId(), currentUserId));
            });

    return PageResponse.from(items);
  }

  public ConversationResponse getConversation(Long conversationId, Long currentUserId) {
    Conversation conversation =
        accessChecker.getConversationWithDetails(conversationId, currentUserId);
    return conversationMapper.toResponse(conversation);
  }

  @Transactional
  public void markRead(Long conversationId, Long currentUserId, MarkReadRequest request) {
    accessChecker.getConversationReference(conversationId, currentUserId);
    boolean messageBelongsToConversation =
        messageRepository.existsByIdAndConversationId(request.messageId(), conversationId);
    if (!messageBelongsToConversation) {
      throw new BadRequestException("Message does not belong to this conversation");
    }
    conversationRepository.markRead(conversationId, currentUserId, request.messageId());
  }
}
