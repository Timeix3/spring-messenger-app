package com.timeix3.messenger.mapper;

import com.timeix3.messenger.dto.conversation.ConversationResponse;
import com.timeix3.messenger.entity.Conversation;
import org.mapstruct.Mapper;

@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, MessageMapper.class})
public interface ConversationMapper {
  ConversationResponse toResponse(Conversation conversation);
}
