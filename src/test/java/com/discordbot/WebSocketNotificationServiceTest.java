package com.discordbot;

import com.discordbot.web.service.WebSocketNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class WebSocketNotificationServiceTest {

    @Test
    @DisplayName("Broadcast join/leave/roles events to /topic/guild-updates with expected payloads")
    void broadcasts() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService svc = new WebSocketNotificationService(template);

        // Guild joined
        svc.notifyGuildJoined("g1", "Guild One");
        // Guild left
        svc.notifyGuildLeft("g2", "Guild Two");
        // Roles changed
        svc.notifyRolesChanged("g3", "updated");

        ArgumentCaptor<String> dest = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(3)).convertAndSend(dest.capture(), payload.capture());

        assertTrue(dest.getAllValues().stream().allMatch("/topic/guild-updates"::equals));

        @SuppressWarnings("unchecked")
        var maps = payload.getAllValues().stream().map(o -> (Map<String, String>) o).toList();
        assertEquals("GUILD_JOINED", maps.get(0).get("type"));
        assertEquals("g1", maps.get(0).get("guildId"));
        assertEquals("Guild One", maps.get(0).get("guildName"));

        assertEquals("GUILD_LEFT", maps.get(1).get("type"));
        assertEquals("g2", maps.get(1).get("guildId"));
        assertEquals("Guild Two", maps.get(1).get("guildName"));

        assertEquals("ROLES_CHANGED", maps.get(2).get("type"));
        assertEquals("g3", maps.get(2).get("guildId"));
        assertEquals("updated", maps.get(2).get("action"));
    }

    @Test
    @DisplayName("Gracefully handle convertAndSend exceptions in notifications")
    void handlesExceptions() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    doThrow(new RuntimeException("boom")).when(template).convertAndSend(anyString(), any(Object.class));

        WebSocketNotificationService svc = new WebSocketNotificationService(template);
        // Should not throw
        assertDoesNotThrow(() -> svc.notifyGuildJoined("g1", "Guild One"));
        assertDoesNotThrow(() -> svc.notifyGuildLeft("g2", "Guild Two"));
        assertDoesNotThrow(() -> svc.notifyRolesChanged("g3", "created"));
    }

    @Test
    @DisplayName("Broadcast QOTD questions changed event to /topic/guild-updates")
    void broadcastsQotdQuestionsChanged() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService svc = new WebSocketNotificationService(template);

        // Notify questions changed
        svc.notifyQotdQuestionsChanged("g123", "ch456", "added");

        ArgumentCaptor<String> dest = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(1)).convertAndSend(dest.capture(), payload.capture());

        assertEquals("/topic/guild-updates", dest.getValue());

        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) payload.getValue();
        assertEquals("QOTD_QUESTIONS_CHANGED", map.get("type"));
        assertEquals("g123", map.get("guildId"));
        assertEquals("ch456", map.get("channelId"));
        assertEquals("added", map.get("action"));
    }

    @Test
    @DisplayName("Broadcast QOTD submissions changed event to /topic/guild-updates")
    void broadcastsQotdSubmissionsChanged() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        WebSocketNotificationService svc = new WebSocketNotificationService(template);

        // Notify submissions changed
        svc.notifyQotdSubmissionsChanged("g789", "approved");

        ArgumentCaptor<String> dest = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(1)).convertAndSend(dest.capture(), payload.capture());

        assertEquals("/topic/guild-updates", dest.getValue());

        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) payload.getValue();
        assertEquals("QOTD_SUBMISSIONS_CHANGED", map.get("type"));
        assertEquals("g789", map.get("guildId"));
        assertEquals("approved", map.get("action"));
    }

    @Test
    @DisplayName("Handle exceptions in QOTD notification methods")
    void handlesQotdNotificationExceptions() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        doThrow(new RuntimeException("boom")).when(template).convertAndSend(anyString(), any(Object.class));

        WebSocketNotificationService svc = new WebSocketNotificationService(template);
        // Should not throw
        assertDoesNotThrow(() -> svc.notifyQotdQuestionsChanged("g1", "ch1", "deleted"));
        assertDoesNotThrow(() -> svc.notifyQotdSubmissionsChanged("g2", "rejected"));
    }
}
