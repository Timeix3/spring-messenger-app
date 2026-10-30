package com.timeix3.messenger.dto.conversation;

import com.timeix3.messenger.dto.message.MessageResponse;
import com.timeix3.messenger.dto.user.UserResponse;

import java.time.Instant;

public record ConversationListItemResponse(
        Long id,
        UserResponse otherUser,
        MessageResponse lastMessage,
        Instant lastMessageAt,
        Long unreadCount
) {
}
