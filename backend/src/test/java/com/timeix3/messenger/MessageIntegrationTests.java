package com.timeix3.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.timeix3.messenger.controller.ChatWebSocketController;
import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.dto.message.MessageResponse;
import com.timeix3.messenger.dto.message.SendMessageRequest;
import com.timeix3.messenger.entity.Conversation;
import com.timeix3.messenger.entity.Message;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.ForbiddenException;
import com.timeix3.messenger.exception.NotFoundException;
import com.timeix3.messenger.mapper.MessageMapper;
import com.timeix3.messenger.repository.MessageRepository;
import com.timeix3.messenger.service.MessageService;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class MessageIntegrationTests extends IntegrationTestSupport {

  @Autowired private MessageRepository messageRepository;

  @Autowired private MessageMapper messageMapper;

  @Autowired private MessageService messageService;

  @Autowired private ChatWebSocketController chatWebSocketController;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void entityAndRepositoryPersistMessagesAndSupportConversationQueries() {
    User sender = saveUser();
    User recipient = saveUser();
    Long conversationId = createConversation(sender.getId(), recipient.getId());
    Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();

    Message first = saveMessage(conversation, sender, "First message");
    Message second = saveMessage(conversation, recipient, "Second message");

    assertNotNull(first.getCreatedAt());
    assertEquals(
        first.getId(),
        jdbcTemplate.queryForObject(
            "select id from messages where content = ?", Long.class, "First message"));
    assertEquals(2, messageRepository.countByConversationId(conversationId));
    assertEquals(
        second.getId(),
        messageRepository
            .findFirstByConversationIdOrderByIdDesc(conversationId)
            .orElseThrow()
            .getId());
    assertEquals(
        second.getId(),
        messageRepository
            .findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, 1))
            .getContent()
            .getFirst()
            .getId());
    assertEquals(
        first.getId(),
        messageRepository
            .findByConversationIdOrderByIdAsc(conversationId, PageRequest.of(0, 1))
            .getContent()
            .getFirst()
            .getId());
    assertEquals(
        first.getId(),
        messageRepository
            .findByConversationIdAndIdLessThanOrderByIdDesc(
                conversationId, second.getId(), PageRequest.of(0, 10))
            .getContent()
            .getFirst()
            .getId());
    assertEquals(
        second.getId(),
        messageRepository
            .findByConversationIdAndIdGreaterThanOrderByIdAsc(conversationId, first.getId())
            .getFirst()
            .getId());
    assertEquals(
        sender.getUsername(),
        messageRepository
            .findByConversationIdOrderByIdAsc(conversationId, PageRequest.of(0, 1))
            .getContent()
            .getFirst()
            .getSender()
            .getUsername());
    assertEquals(
        conversationId,
        messageRepository
            .findByIdAndConversationId(first.getId(), conversationId)
            .orElseThrow()
            .getConversation()
            .getId());
    assertTrue(messageRepository.existsByIdAndConversationId(first.getId(), conversationId));
    assertFalse(messageRepository.existsByIdAndConversationId(Long.MAX_VALUE, conversationId));
  }

  @Test
  void mapperConvertsSendRequestsAndMessagesToResponses() {
    User sender = saveUser();
    User recipient = saveUser();
    Long conversationId = createConversation(sender.getId(), recipient.getId());
    Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
    Message message = saveMessage(conversation, sender, "Mapped message");

    MessageResponse response = messageMapper.toResponse(message);
    assertEquals(message.getId(), response.id());
    assertEquals(conversationId, response.conversationId());
    assertEquals(sender.getId(), response.sender().id());
    assertEquals(sender.getUsername(), response.sender().username());
    assertEquals("Mapped message", response.content());
    assertEquals(message.getCreatedAt(), response.createdAt());

    Message mapped = messageMapper.toEntity(new SendMessageRequest("Request message"));
    assertNull(mapped.getId());
    assertNull(mapped.getConversation());
    assertNull(mapped.getSender());
    assertNull(mapped.getCreatedAt());
    assertEquals("Request message", mapped.getContent());
  }

  @Test
  void serviceSendsMessagesUpdatesConversationAndReturnsPagedHistory() {
    User sender = saveUser();
    User recipient = saveUser();
    User outsider = saveUser();
    Long conversationId = createConversation(sender.getId(), recipient.getId());

    MessageResponse first =
        messageService.sendMessage(
            conversationId, sender.getId(), new SendMessageRequest("Service message 1"));
    MessageResponse second =
        messageService.sendMessage(
            conversationId, recipient.getId(), new SendMessageRequest("Service message 2"));
    MessageResponse third =
        messageService.sendMessage(
            conversationId, sender.getId(), new SendMessageRequest("Service message 3"));

    Conversation updatedConversation =
        conversationRepository.findById(conversationId).orElseThrow();
    assertEquals(third.id(), updatedConversation.getLastMessage().getId());
    assertEquals(third.createdAt(), updatedConversation.getLastMessageAt());

    PageResponse<MessageResponse> firstPage =
        messageService.getMessages(conversationId, sender.getId(), PageRequest.of(0, 2));
    assertEquals(3, firstPage.totalElements());
    assertEquals(2, firstPage.content().size());
    assertEquals(third.id(), firstPage.content().get(0).id());
    assertEquals(second.id(), firstPage.content().get(1).id());
    assertEquals(0, firstPage.page());
    assertEquals(2, firstPage.size());

    PageResponse<MessageResponse> secondPage =
        messageService.getMessages(conversationId, sender.getId(), PageRequest.of(1, 2));
    assertEquals(1, secondPage.content().size());
    assertEquals(first.id(), secondPage.content().getFirst().id());
    assertThrows(
        ForbiddenException.class,
        () -> messageService.getMessages(conversationId, outsider.getId(), PageRequest.of(0, 10)));
    assertThrows(
        NotFoundException.class,
        () -> messageService.getMessages(Long.MAX_VALUE, sender.getId(), PageRequest.of(0, 10)));
  }

  @Test
  void websocketControllerSendsMessagesForTheAuthenticatedPrincipal() {
    User sender = saveUser();
    User recipient = saveUser();
    Long conversationId = createConversation(sender.getId(), recipient.getId());

    chatWebSocketController.sendMessage(
        conversationId,
        new SendMessageRequest("WebSocket message"),
        () -> sender.getId().toString());

    Message saved =
        messageRepository.findFirstByConversationIdOrderByIdDesc(conversationId).orElseThrow();
    assertEquals(sender.getId(), saved.getSender().getId());
    assertEquals("WebSocket message", saved.getContent());
  }

  @Test
  void controllerSendsAndPagesMessagesAndRequiresAuthentication() throws Exception {
    AuthResponse sender = authResponse(register(uniqueUsername(), "Message Sender"));
    AuthResponse recipient = authResponse(register(uniqueUsername(), "Message Recipient"));
    AuthResponse outsider = authResponse(register(uniqueUsername(), "Message Outsider"));
    Long conversationId = createConversation(sender.userId(), recipient.userId());
    String path = "/api/conversations/" + conversationId + "/messages";

    HttpResponse<String> sentFirst =
        post(path, Map.of("content", "Controller message 1"), sender.accessToken());
    HttpResponse<String> sentSecond =
        post(path, Map.of("content", "Controller message 2"), recipient.accessToken());
    HttpResponse<String> sentThird =
        post(path, Map.of("content", "Controller message 3"), sender.accessToken());
    assertEquals(HttpStatus.CREATED.value(), sentFirst.statusCode());
    assertEquals(HttpStatus.CREATED.value(), sentSecond.statusCode());
    assertEquals(HttpStatus.CREATED.value(), sentThird.statusCode());

    JsonNode thirdMessage = objectMapper.readTree(sentThird.body());
    assertEquals(conversationId, thirdMessage.get("conversationId").asLong());
    assertEquals(sender.userId(), thirdMessage.get("sender").get("id").asLong());
    assertEquals("Message Sender", thirdMessage.get("sender").get("displayName").asText());
    assertEquals("Controller message 3", thirdMessage.get("content").asText());
    assertTrue(thirdMessage.hasNonNull("createdAt"));

    HttpResponse<String> firstPageResponse = get(path + "?page=0&size=2", sender.accessToken());
    assertEquals(HttpStatus.OK.value(), firstPageResponse.statusCode());
    JsonNode firstPage = objectMapper.readTree(firstPageResponse.body());
    assertEquals(3, firstPage.get("totalElements").asLong());
    assertEquals(2, firstPage.get("content").size());
    assertEquals(
        thirdMessage.get("id").asLong(), firstPage.get("content").get(0).get("id").asLong());
    assertEquals(0, firstPage.get("page").asInt());
    assertEquals(2, firstPage.get("size").asInt());
    assertTrue(
        firstPage.get("content").get(0).get("id").asLong()
            > firstPage.get("content").get(1).get("id").asLong());

    HttpResponse<String> secondPage = get(path + "?page=1&size=2", recipient.accessToken());
    assertEquals(HttpStatus.OK.value(), secondPage.statusCode());
    assertEquals(1, objectMapper.readTree(secondPage.body()).get("content").size());

    assertEquals(
        HttpStatus.BAD_REQUEST.value(),
        post(path, Map.of("content", "   "), sender.accessToken()).statusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST.value(),
        post(path, Map.of("content", "x".repeat(4001)), sender.accessToken()).statusCode());
    assertEquals(HttpStatus.FORBIDDEN.value(), get(path, outsider.accessToken()).statusCode());
    assertEquals(HttpStatus.UNAUTHORIZED.value(), get(path, null).statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(),
        post(path, Map.of("content", "Unauthenticated message"), null).statusCode());
    assertEquals(
        HttpStatus.NOT_FOUND.value(),
        get("/api/conversations/" + Long.MAX_VALUE + "/messages", sender.accessToken())
            .statusCode());
  }

  private User saveUser() {
    User user = new User();
    user.setUsername(uniqueUsername());
    user.setPasswordHash(passwordEncoder.encode("Correct-Horse-123"));
    user.setDisplayName("Message Test User");
    return userRepository.saveAndFlush(user);
  }

  private Message saveMessage(Conversation conversation, User sender, String content) {
    Message message = new Message();
    message.setConversation(conversation);
    message.setSender(sender);
    message.setContent(content);
    return messageRepository.saveAndFlush(message);
  }
}
