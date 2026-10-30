package com.timeix3.messenger.service;

import com.timeix3.messenger.entity.Conversation;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.ForbiddenException;
import com.timeix3.messenger.exception.NotFoundException;
import com.timeix3.messenger.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConversationAccessChecker {

    private final ConversationRepository conversationRepository;

    public Conversation getConversationWithDetails(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository
                .findWithDetailsById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        assertParticipant(conversation, userId);

        return conversation;
    }

    public Conversation getConversationReference(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        assertParticipant(conversation, userId);

        return conversation;
    }

    public User getParticipant(Conversation conversation, Long userId) {
        if (isSameUser(conversation.getUser1(), userId)) {
            return conversation.getUser1();
        }

        if (isSameUser(conversation.getUser2(), userId)) {
            return conversation.getUser2();
        }

        throw new ForbiddenException("You do not have access to this conversation");
    }

    public void assertParticipant(Conversation conversation, Long userId) {
        getParticipant(conversation, userId);
    }

    public User resolveOtherUser(Conversation conversation, Long userId) {
        if (isSameUser(conversation.getUser1(), userId)) {
            return conversation.getUser2();
        }

        if (isSameUser(conversation.getUser2(), userId)) {
            return conversation.getUser1();
        }

        throw new ForbiddenException("You do not have access to this conversation");
    }

    private boolean isSameUser(User user, Long userId) {
        return user != null
                && user.getId() != null
                && user.getId().equals(userId);
    }
}
