package com.timeix3.messenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.timeix3.messenger.dto.PageResponse;
import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.dto.auth.RegisterRequest;
import com.timeix3.messenger.dto.user.UserResponse;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.NotFoundException;
import com.timeix3.messenger.mapper.UserMapper;
import com.timeix3.messenger.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void entityAndRepositoryPersistUsersAndSupportUserQueries() {
        String searchMarker = uniqueUsername();
        User user = createUser(uniqueUsername(), "Integration Search " + searchMarker);
        User saved = userRepository.saveAndFlush(user);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(saved.getUsername(), jdbcTemplate.queryForObject(
                "select username from users where id = ?",
                String.class,
                saved.getId()
        ));
        assertTrue(userRepository.existsByUsernameIgnoreCase(saved.getUsername().toUpperCase()));
        assertEquals(saved.getId(), userRepository.findByUsernameIgnoreCase(
                saved.getUsername().toUpperCase()
        ).orElseThrow().getId());

        assertEquals(saved.getId(), userRepository.searchUsers(searchMarker, PageRequest.of(0, 10))
                .getContent().getFirst().getId());
        assertEquals(saved.getId(), userRepository.searchUsers(
                saved.getUsername().toUpperCase(),
                PageRequest.of(0, 10)
        ).getContent().getFirst().getId());

        User other = userRepository.saveAndFlush(createUser(uniqueUsername(), "Another User"));
        List<User> publicUsers = userRepository.findAllPublicUsers(
                saved.getId(),
                PageRequest.of(0, 100)
        ).getContent();
        assertFalse(publicUsers.stream().anyMatch(candidate -> candidate.getId().equals(saved.getId())));
        assertTrue(publicUsers.stream().anyMatch(candidate -> candidate.getId().equals(other.getId())));
    }

    @Test
    void mapperMapsUserAndRegistrationRequestWithoutExposingPasswordHash() {
        User user = createUser(uniqueUsername(), "Mapped User");
        user.setId(42L);
        Instant createdAt = Instant.parse("2024-01-02T03:04:05Z");
        user.setCreatedAt(createdAt);

        UserResponse response = userMapper.toResponse(user);
        assertEquals(42L, response.id());
        assertEquals(user.getUsername(), response.username());
        assertEquals("Mapped User", response.displayName());
        assertEquals(createdAt, response.createdAt());

        RegisterRequest request = new RegisterRequest("mapper_user", "Valid-Password-123", " Mapper ");
        User mapped = userMapper.toEntity(request);
        assertEquals("mapper_user", mapped.getUsername());
        assertEquals(" Mapper ", mapped.getDisplayName());
        assertNull(mapped.getId());
        assertNull(mapped.getPasswordHash());
        assertNull(mapped.getCreatedAt());
        assertNull(mapped.getUpdatedAt());

        AuthResponse authResponse = userMapper.toAuthResponse(user, "Bearer", "token", 900);
        assertEquals(42L, authResponse.userId());
        assertEquals(user.getUsername(), authResponse.username());
        assertEquals("Mapped User", authResponse.displayName());
        assertEquals("token", authResponse.accessToken());
    }

    @Test
    void serviceReturnsMappedUsersSearchResultsAndEmptyPages() {
        User user = userRepository.saveAndFlush(createUser(uniqueUsername(), "Service Search Name"));

        UserResponse response = userService.getUserById(user.getId());
        assertEquals(user.getId(), response.id());
        assertEquals(user.getUsername(), response.username());
        assertThrows(NotFoundException.class, () -> userService.getUserById(Long.MAX_VALUE));

        PageResponse<UserResponse> results = userService.searchUsers(
                "  SERVICE SEARCH  ",
                PageRequest.of(0, 1)
        );
        assertEquals(1, results.totalElements());
        assertEquals(user.getId(), results.content().getFirst().id());
        assertEquals(0, results.page());
        assertEquals(1, results.size());

        PageResponse<UserResponse> empty = userService.searchUsers("  ", PageRequest.of(2, 5));
        assertTrue(empty.content().isEmpty());
        assertEquals(2, empty.page());
        assertEquals(5, empty.size());
        assertEquals(0, empty.totalElements());
    }

    @Test
    void userControllerServesAuthenticatedProfileLookupAndPaginatedSearch() throws Exception {
        String username = uniqueUsername();
        AuthResponse current = authResponse(register(username, "Current Profile"));
        AuthResponse matching = authResponse(register(uniqueUsername(), "User Directory Match"));
        authResponse(register(uniqueUsername(), "Unrelated Profile"));

        HttpResponse<String> profile = get("/api/users/me", current.accessToken());
        assertEquals(HttpStatus.OK.value(), profile.statusCode());
        JsonNode profileJson = objectMapper.readTree(profile.body());
        assertEquals(current.userId(), profileJson.get("id").asLong());
        assertEquals(username, profileJson.get("username").asText());
        assertEquals("Current Profile", profileJson.get("displayName").asText());
        assertFalse(profileJson.has("passwordHash"));

        HttpResponse<String> byId = get("/api/users/" + matching.userId(), current.accessToken());
        assertEquals(HttpStatus.OK.value(), byId.statusCode());
        assertEquals(matching.userId(), objectMapper.readTree(byId.body()).get("id").asLong());

        HttpResponse<String> search = get(
                "/api/users/search?q=directory%20match&page=0&size=1",
                current.accessToken()
        );
        assertEquals(HttpStatus.OK.value(), search.statusCode());
        JsonNode page = objectMapper.readTree(search.body());
        assertEquals(1, page.get("totalElements").asLong());
        assertEquals(1, page.get("content").size());
        assertEquals(matching.userId(), page.get("content").get(0).get("id").asLong());
        assertEquals(0, page.get("page").asInt());
        assertEquals(1, page.get("size").asInt());

        HttpResponse<String> emptySearch = get("/api/users/search?q=%20%20", current.accessToken());
        assertEquals(HttpStatus.OK.value(), emptySearch.statusCode());
        assertTrue(objectMapper.readTree(emptySearch.body()).get("content").isEmpty());

        HttpResponse<String> directory = get("/api/users/directory?page=0&size=100", current.accessToken());
        assertEquals(HttpStatus.OK.value(), directory.statusCode());
        JsonNode directoryContent = objectMapper.readTree(directory.body()).get("content");
        assertTrue(directoryContent.size() >= 2);
        assertTrue(directoryContent.findValuesAsText("id").contains(matching.userId().toString()));
        assertFalse(directoryContent.findValuesAsText("id").contains(current.userId().toString()));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), get("/api/users/me", null).statusCode());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), get("/api/users/search?q=user", null).statusCode());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), get("/api/users/directory", null).statusCode());
    }

    private User createUser(String username, String displayName) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Correct-Horse-123"));
        user.setDisplayName(displayName);
        return user;
    }
}
