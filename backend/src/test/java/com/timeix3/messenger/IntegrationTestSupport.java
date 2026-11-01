package com.timeix3.messenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.entity.Conversation;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.repository.ConversationRepository;
import com.timeix3.messenger.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = "spring.docker.compose.enabled=false"
)
@Import(TestcontainersConfiguration.class)
abstract class IntegrationTestSupport {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ConversationRepository conversationRepository;

    protected HttpResponse<String> register(String username, String displayName) throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("username", username);
        request.put("password", "Correct-Horse-123");
        if (displayName != null) {
            request.put("displayName", displayName);
        }
        return post("/api/auth/register", request, null);
    }

    protected AuthResponse authResponse(HttpResponse<String> response) throws Exception {
        assertEquals(201, response.statusCode());
        return objectMapper.readValue(response.body(), AuthResponse.class);
    }

    protected HttpResponse<String> post(
            String path,
            Object body,
            String accessToken
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String path, String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    protected String uniqueUsername() {
        return "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    protected Long createConversation(Long firstUserId, Long secondUserId) {
        User first = userRepository.findById(firstUserId).orElseThrow();
        User second = userRepository.findById(secondUserId).orElseThrow();
        Conversation conversation = new Conversation();
        conversation.setUser1(firstUserId < secondUserId ? first : second);
        conversation.setUser2(firstUserId < secondUserId ? second : first);
        return conversationRepository.saveAndFlush(conversation).getId();
    }
}
