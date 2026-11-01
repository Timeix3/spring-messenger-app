package com.timeix3.messenger;

import com.fasterxml.jackson.databind.JsonNode;
import com.timeix3.messenger.dto.PresenceStatus;
import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void presenceServiceTracksMultipleSessionsHeartbeatsAndBatchStatuses() {
        long userId = System.nanoTime();
        long offlineUserId = userId + 1;
        String firstSessionId = uniqueUsername();
        String secondSessionId = uniqueUsername();

        assertEquals(PresenceStatus.OFFLINE, presenceService.getStatus(userId));
        assertNull(presenceService.findUserIdBySession(firstSessionId));
        assertTrue(presenceService.getStatuses(null).isEmpty());
        assertTrue(presenceService.getStatuses(List.of()).isEmpty());

        presenceService.markOnline(userId, firstSessionId);
        presenceService.heartbeat(userId, secondSessionId);

        assertEquals(userId, presenceService.findUserIdBySession(firstSessionId));
        assertEquals(userId, presenceService.findUserIdBySession(secondSessionId));
        assertEquals(PresenceStatus.ONLINE, presenceService.getStatus(userId));
        assertTrue(redisTemplate.getExpire("user:%d:status".formatted(userId)) > 0);
        assertTrue(redisTemplate.getExpire("ws:session:%s".formatted(firstSessionId)) > 0);

        assertEquals(
                Map.of(
                        userId, PresenceStatus.ONLINE,
                        offlineUserId, PresenceStatus.OFFLINE
                ),
                presenceService.getStatuses(Arrays.asList(userId, offlineUserId, userId, null))
        );

        presenceService.markOffline(userId, firstSessionId);
        assertNull(presenceService.findUserIdBySession(firstSessionId));
        assertEquals(PresenceStatus.ONLINE, presenceService.getStatus(userId));

        presenceService.markOffline(userId, secondSessionId);
        assertNull(presenceService.findUserIdBySession(secondSessionId));
        assertEquals(PresenceStatus.OFFLINE, presenceService.getStatus(userId));
        assertEquals(
                PresenceStatus.OFFLINE.name(),
                redisTemplate.opsForValue().get("user:%d:status".formatted(userId))
        );
    }

    @Test
    void presenceServiceIgnoresMissingIdentifiersAndMalformedSessionMappings() {
        String sessionId = uniqueUsername();
        presenceService.markOnline(null, sessionId);
        presenceService.markOnline(123L, null);
        presenceService.heartbeat(null, sessionId);
        presenceService.markOffline(null, sessionId);
        presenceService.markOffline(123L, null);

        assertNull(presenceService.findUserIdBySession(null));
        assertFalse(presenceService.getStatuses(Arrays.asList((Long) null)).containsKey(null));

        String malformedSessionId = uniqueUsername();
        redisTemplate.opsForValue().set("ws:session:%s".formatted(malformedSessionId), "not-a-user-id");
        assertNull(presenceService.findUserIdBySession(malformedSessionId));
    }

    @Test
    void userPresenceControllerReturnsProtectedPaginatedStatuses() throws Exception {
        AuthResponse viewer = authResponse(register(uniqueUsername(), "Presence Viewer"));
        AuthResponse onlineUser = authResponse(register(uniqueUsername(), "Online Presence"));
        AuthResponse offlineUser = authResponse(register(uniqueUsername(), "Offline Presence"));
        presenceService.markOnline(onlineUser.userId(), uniqueUsername());

        HttpResponse<String> onlineStatus = get(
                "/api/users/statuses/" + onlineUser.userId(),
                viewer.accessToken()
        );
        assertEquals(HttpStatus.OK.value(), onlineStatus.statusCode());
        JsonNode onlineJson = objectMapper.readTree(onlineStatus.body());
        assertEquals(onlineUser.userId(), onlineJson.get("userId").asLong());
        assertEquals(onlineUser.username(), onlineJson.get("username").asText());
        assertEquals("Online Presence", onlineJson.get("displayName").asText());
        assertEquals(PresenceStatus.ONLINE.name(), onlineJson.get("status").asText());

        HttpResponse<String> offlineStatus = get(
                "/api/users/statuses/" + offlineUser.userId(),
                viewer.accessToken()
        );
        assertEquals(HttpStatus.OK.value(), offlineStatus.statusCode());
        assertEquals(
                PresenceStatus.OFFLINE.name(),
                objectMapper.readTree(offlineStatus.body()).get("status").asText()
        );

        HttpResponse<String> statuses = get(
                "/api/users/statuses?page=0&size=1000",
                viewer.accessToken()
        );
        assertEquals(HttpStatus.OK.value(), statuses.statusCode());
        JsonNode page = objectMapper.readTree(statuses.body());
        assertEquals(0, page.get("page").asInt());
        assertEquals(1000, page.get("size").asInt());
        assertTrue(page.get("totalElements").asLong() >= 3);
        assertPresence(page.get("content"), onlineUser.userId(), PresenceStatus.ONLINE);
        assertPresence(page.get("content"), offlineUser.userId(), PresenceStatus.OFFLINE);

        HttpResponse<String> firstPage = get(
                "/api/users/statuses?page=0&size=1",
                viewer.accessToken()
        );
        assertEquals(HttpStatus.OK.value(), firstPage.statusCode());
        JsonNode firstPageJson = objectMapper.readTree(firstPage.body());
        assertEquals(1, firstPageJson.get("size").asInt());
        assertEquals(1, firstPageJson.get("content").size());

        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                get("/api/users/statuses", null).statusCode()
        );
        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                get("/api/users/statuses/" + onlineUser.userId(), null).statusCode()
        );
    }

    private void assertPresence(JsonNode users, Long userId, PresenceStatus expectedStatus) {
        for (JsonNode user : users) {
            if (user.get("userId").asLong() == userId) {
                assertEquals(expectedStatus.name(), user.get("status").asText());
                return;
            }
        }
        throw new AssertionError("User " + userId + " was not present in the statuses page");
    }
}
