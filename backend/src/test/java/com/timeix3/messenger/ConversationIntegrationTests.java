package com.timeix3.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.dto.conversation.ConversationListItemResponse;
import com.timeix3.messenger.dto.conversation.ConversationResponse;
import com.timeix3.messenger.dto.conversation.CreateConversationRequest;
import com.timeix3.messenger.dto.message.MarkReadRequest;
import com.timeix3.messenger.entity.Conversation;
import com.timeix3.messenger.entity.Message;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.BadRequestException;
import com.timeix3.messenger.exception.ForbiddenException;
import com.timeix3.messenger.exception.NotFoundException;
import com.timeix3.messenger.mapper.ConversationMapper;
import com.timeix3.messenger.repository.MessageRepository;
import com.timeix3.messenger.service.ConversationService;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

class ConversationIntegrationTests extends IntegrationTestSupport {

  @Autowired private MessageRepository messageRepository;

  @Autowired private ConversationMapper conversationMapper;

  @Autowired private ConversationService conversationService;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @Transactional
  void entityAndRepositoryPersistConversationsAndSupportPairAndUnreadQueries() {
    User firstUser = saveUser();
    User secondUser = saveUser();
    User thirdUser = saveUser();
    Long conversationId = createConversation(firstUser.getId(), secondUser.getId());
    Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();

    assertNotNull(conversation.getCreatedAt());
    assertEquals(Math.min(firstUser.getId(), secondUser.getId()), conversation.getUser1().getId());
    assertEquals(Math.max(firstUser.getId(), secondUser.getId()), conversation.getUser2().getId());
    assertEquals(
        conversationId,
        jdbcTemplate.queryForObject(
            "select id from conversations where user1_id = ? and user2_id = ?",
            Long.class,
            conversation.getUser1().getId(),
            conversation.getUser2().getId()));
    assertEquals(
        conversationId,
        conversationRepository
            .findBetweenUsers(firstUser.getId(), secondUser.getId())
            .orElseThrow()
            .getId());
    assertEquals(
        conversationId,
        conversationRepository
            .findBetweenUsers(secondUser.getId(), firstUser.getId())
            .orElseThrow()
            .getId());
    assertEquals(
        1, conversationRepository.countBetweenUsers(firstUser.getId(), secondUser.getId()));
    assertEquals(0, conversationRepository.countBetweenUsers(firstUser.getId(), thirdUser.getId()));

    Message firstMessage = saveMessage(conversation, secondUser, "First message");
    Message secondMessage = saveMessage(conversation, firstUser, "Second message");
    assertEquals(1, conversationRepository.countUnreadMessages(conversationId, firstUser.getId()));
    assertEquals(1, conversationRepository.countUnreadMessages(conversationId, secondUser.getId()));
    assertEquals(0, conversationRepository.countUnreadMessages(conversationId, thirdUser.getId()));
    assertEquals(
        1,
        conversationRepository.markRead(conversationId, firstUser.getId(), firstMessage.getId()));
    assertEquals(0, conversationRepository.countUnreadMessages(conversationId, firstUser.getId()));
    assertEquals(
        0,
        conversationRepository.markRead(conversationId, thirdUser.getId(), secondMessage.getId()));

    Long otherConversationId = createConversation(firstUser.getId(), thirdUser.getId());
    Conversation otherConversation =
        conversationRepository.findById(otherConversationId).orElseThrow();
    Message otherMessage = saveMessage(otherConversation, thirdUser, "Other conversation");
    Instant earlier = Instant.parse("2024-01-01T00:00:00Z");
    Instant later = Instant.parse("2024-01-02T00:00:00Z");
    conversationRepository.updateLastMessage(conversationId, secondMessage.getId(), earlier);
    conversationRepository.updateLastMessage(otherConversationId, otherMessage.getId(), later);

    var page = conversationRepository.findAllByUserId(firstUser.getId(), PageRequest.of(0, 1));
    assertEquals(2, page.getTotalElements());
    assertEquals(otherConversationId, page.getContent().getFirst().getId());
    assertEquals(otherMessage.getId(), page.getContent().getFirst().getLastMessage().getId());
    assertNotNull(page.getContent().getFirst().getUser1().getUsername());
    assertNotNull(page.getContent().getFirst().getUser2().getUsername());

    Conversation loaded = conversationRepository.findWithDetailsById(conversationId).orElseThrow();
    assertEquals(secondMessage.getId(), loaded.getLastMessage().getId());
    assertEquals(earlier, loaded.getLastMessageAt());
    assertTrue(
        conversationRepository.findBetweenUsers(firstUser.getId(), thirdUser.getId()).isPresent());
  }

  @Test
  @Transactional
  void mapperMapsConversationDetailsAndNullLastMessage() {
    User firstUser = saveUser();
    User secondUser = saveUser();
    Long conversationId = createConversation(firstUser.getId(), secondUser.getId());

    Conversation emptyConversation =
        conversationRepository.findWithDetailsById(conversationId).orElseThrow();
    ConversationResponse emptyResponse = conversationMapper.toResponse(emptyConversation);
    assertEquals(conversationId, emptyResponse.id());
    assertEquals(emptyConversation.getUser1().getId(), emptyResponse.user1().id());
    assertEquals(emptyConversation.getUser2().getId(), emptyResponse.user2().id());
    assertNull(emptyResponse.lastMessage());
    assertNull(emptyResponse.lastMessageAt());
    assertNotNull(emptyResponse.createdAt());

    Message message =
        saveMessage(emptyConversation, emptyConversation.getUser1(), "Mapped conversation");
    conversationRepository.updateLastMessage(
        conversationId, message.getId(), message.getCreatedAt());
    Conversation populated =
        conversationRepository.findWithDetailsById(conversationId).orElseThrow();
    ConversationResponse response = conversationMapper.toResponse(populated);

    assertEquals(message.getId(), response.lastMessage().id());
    assertEquals(conversationId, response.lastMessage().conversationId());
    assertEquals("Mapped conversation", response.lastMessage().content());
    assertEquals(emptyConversation.getUser1().getId(), response.lastMessage().sender().id());
    assertEquals(message.getCreatedAt(), response.lastMessageAt());
  }

  @Test
  void serviceCreatesConversationOnceListsUnreadAndEnforcesAccess() {
    User firstUser = saveUser();
    User secondUser = saveUser();
    User outsider = saveUser();

    ConversationResponse created =
        conversationService.createConversation(
            firstUser.getId(), new CreateConversationRequest(secondUser.getId()));
    ConversationResponse existing =
        conversationService.createConversation(
            secondUser.getId(), new CreateConversationRequest(firstUser.getId()));
    assertEquals(created.id(), existing.id());
    assertEquals(
        1, conversationRepository.countBetweenUsers(firstUser.getId(), secondUser.getId()));
    assertEquals(Math.min(firstUser.getId(), secondUser.getId()), created.user1().id());
    assertEquals(Math.max(firstUser.getId(), secondUser.getId()), created.user2().id());

    Conversation conversation = conversationRepository.findById(created.id()).orElseThrow();
    Message received = saveMessage(conversation, secondUser, "Unread message");
    conversation.setLastMessage(received);
    conversation.setLastMessageAt(received.getCreatedAt());
    conversationRepository.saveAndFlush(conversation);
    PageResponse<ConversationListItemResponse> conversations =
        conversationService.getCurrentUserConversations(firstUser.getId(), PageRequest.of(0, 10));
    assertEquals(1, conversations.totalElements());
    assertEquals(secondUser.getId(), conversations.content().getFirst().otherUser().id());
    assertEquals(received.getId(), conversations.content().getFirst().lastMessage().id());
    assertEquals(1, conversations.content().getFirst().unreadCount());

    conversationService.markRead(
        created.id(), firstUser.getId(), new MarkReadRequest(received.getId()));
    assertEquals(
        0,
        conversationService
            .getCurrentUserConversations(firstUser.getId(), PageRequest.of(0, 10))
            .content()
            .getFirst()
            .unreadCount());
    assertThrows(
        BadRequestException.class,
        () ->
            conversationService.markRead(
                created.id(), firstUser.getId(), new MarkReadRequest(Long.MAX_VALUE)));
    assertThrows(
        ForbiddenException.class,
        () -> conversationService.getConversation(created.id(), outsider.getId()));
    assertThrows(
        NotFoundException.class,
        () -> conversationService.getConversation(Long.MAX_VALUE, firstUser.getId()));
    assertThrows(
        BadRequestException.class,
        () ->
            conversationService.createConversation(
                firstUser.getId(), new CreateConversationRequest(firstUser.getId())));
    assertThrows(
        NotFoundException.class,
        () ->
            conversationService.createConversation(
                firstUser.getId(), new CreateConversationRequest(Long.MAX_VALUE)));
  }

  @Test
  void controllerCreatesListsReadsAndProtectsConversations() throws Exception {
    AuthResponse firstUser = authResponse(register(uniqueUsername(), "Conversation One"));
    AuthResponse secondUser = authResponse(register(uniqueUsername(), "Conversation Two"));
    AuthResponse outsider = authResponse(register(uniqueUsername(), "Conversation Outsider"));

    HttpResponse<String> createdResponse =
        post(
            "/api/conversations",
            Map.of("companionId", secondUser.userId()),
            firstUser.accessToken());
    assertEquals(HttpStatus.CREATED.value(), createdResponse.statusCode());
    JsonNode created = objectMapper.readTree(createdResponse.body());
    long conversationId = created.get("id").asLong();
    assertTrue(created.get("createdAt").isTextual());
    assertEquals(firstUser.userId(), created.get("user1").get("id").asLong());
    assertEquals(secondUser.userId(), created.get("user2").get("id").asLong());
    assertTrue(created.get("lastMessage").isNull());

    HttpResponse<String> duplicate =
        post(
            "/api/conversations",
            Map.of("companionId", firstUser.userId()),
            secondUser.accessToken());
    assertEquals(HttpStatus.CREATED.value(), duplicate.statusCode());
    assertEquals(conversationId, objectMapper.readTree(duplicate.body()).get("id").asLong());
    assertEquals(
        HttpStatus.BAD_REQUEST.value(),
        post("/api/conversations", Map.of("companionId", 0), firstUser.accessToken()).statusCode());
    assertEquals(
        HttpStatus.NOT_FOUND.value(),
        post("/api/conversations", Map.of("companionId", Long.MAX_VALUE), firstUser.accessToken())
            .statusCode());

    Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
    User sender = userRepository.findById(secondUser.userId()).orElseThrow();
    Message unreadMessage = saveMessage(conversation, sender, "Controller unread");
    conversation.setLastMessage(unreadMessage);
    conversation.setLastMessageAt(unreadMessage.getCreatedAt());
    conversationRepository.saveAndFlush(conversation);

    HttpResponse<String> details =
        get("/api/conversations/" + conversationId, secondUser.accessToken());
    assertEquals(HttpStatus.OK.value(), details.statusCode());
    assertEquals(conversationId, objectMapper.readTree(details.body()).get("id").asLong());
    assertEquals(
        HttpStatus.FORBIDDEN.value(),
        get("/api/conversations/" + conversationId, outsider.accessToken()).statusCode());
    assertEquals(
        HttpStatus.NOT_FOUND.value(),
        get("/api/conversations/" + Long.MAX_VALUE, firstUser.accessToken()).statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(),
        get("/api/conversations/" + conversationId, null).statusCode());

    HttpResponse<String> list = get("/api/conversations?page=0&size=1", firstUser.accessToken());
    assertEquals(HttpStatus.OK.value(), list.statusCode());
    JsonNode page = objectMapper.readTree(list.body());
    assertEquals(1, page.get("totalElements").asLong());
    assertEquals(conversationId, page.get("content").get(0).get("id").asLong());
    assertEquals(
        secondUser.userId(), page.get("content").get(0).get("otherUser").get("id").asLong());
    assertEquals(1, page.get("content").get(0).get("unreadCount").asLong());

    HttpResponse<String> markedRead =
        post(
            "/api/conversations/" + conversationId + "/read",
            Map.of("messageId", unreadMessage.getId()),
            firstUser.accessToken());
    assertEquals(HttpStatus.NO_CONTENT.value(), markedRead.statusCode());
    JsonNode readPage =
        objectMapper.readTree(get("/api/conversations", firstUser.accessToken()).body());
    assertEquals(0, readPage.get("content").get(0).get("unreadCount").asLong());

    assertEquals(
        HttpStatus.BAD_REQUEST.value(),
        post(
                "/api/conversations/" + conversationId + "/read",
                Map.of("messageId", 0),
                firstUser.accessToken())
            .statusCode());
    assertEquals(
        HttpStatus.FORBIDDEN.value(),
        post(
                "/api/conversations/" + conversationId + "/read",
                Map.of("messageId", unreadMessage.getId()),
                outsider.accessToken())
            .statusCode());
    assertEquals(
        HttpStatus.UNAUTHORIZED.value(),
        post("/api/conversations", Map.of("companionId", secondUser.userId()), null).statusCode());
  }

  private User saveUser() {
    User user = new User();
    user.setUsername(uniqueUsername());
    user.setPasswordHash(passwordEncoder.encode("Correct-Horse-123"));
    user.setDisplayName("Conversation Test User");
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
