package com.timeix3.messenger.mapper;

import com.timeix3.messenger.dto.message.MessageResponse;
import com.timeix3.messenger.dto.message.SendMessageRequest;
import com.timeix3.messenger.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface MessageMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    MessageResponse toResponse(Message message);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "conversation", ignore = true)
    @Mapping(target = "sender", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Message toEntity(SendMessageRequest request);
}
