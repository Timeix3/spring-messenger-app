package com.timeix3.messenger.service;

import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.message.MessageResponse;
import com.timeix3.messenger.dto.message.SendMessageRequest;
import com.timeix3.messenger.entity.Conversation;
import com.timeix3.messenger.entity.Message;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.mapper.MessageMapper;
import com.timeix3.messenger.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final ConversationAccessChecker accessChecker;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRateLimiter messageRateLimiter;

    @Transactional
    public MessageResponse sendMessage(Long conversationId, Long senderId, SendMessageRequest request) {
        messageRateLimiter.checkAllowed(senderId);
        Conversation conversation = accessChecker.getConversationWithDetails(conversationId, senderId);
        User sender = accessChecker.getParticipant(conversation, senderId);
        Message message = messageMapper.toEntity(request);
        message.setConversation(conversation);
        message.setSender(sender);

        Message savedMessage = messageRepository.save(message);
        conversation.setLastMessage(savedMessage);
        conversation.setLastMessageAt(savedMessage.getCreatedAt());
        MessageResponse response = messageMapper.toResponse(savedMessage);

        sendToWebSocketAfterCommit(conversationId, response);
        return response;
    }

    public PageResponse<MessageResponse> getMessages(Long conversationId, Long currentUserId, Pageable pageable) {
        accessChecker.getConversationReference(conversationId, currentUserId);
        Page<Message> messages = messageRepository.findByConversationIdOrderByIdDesc(conversationId, pageable);
        Page<MessageResponse> mapped = messages.map(messageMapper::toResponse);
        return PageResponse.from(mapped);
    }

    private void sendToWebSocketAfterCommit(Long conversationId, MessageResponse response) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);
            }
        });
    }
}
