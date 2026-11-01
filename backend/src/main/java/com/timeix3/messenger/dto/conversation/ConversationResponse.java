package com.timeix3.messenger.dto.conversation;

import com.timeix3.messenger.dto.message.MessageResponse;
import com.timeix3.messenger.dto.user.UserResponse;
import java.time.Instant;

public record ConversationResponse(
    Long id,
    UserResponse user1,
    UserResponse user2,
    MessageResponse lastMessage,
    Instant lastMessageAt,
    Instant createdAt) {}
