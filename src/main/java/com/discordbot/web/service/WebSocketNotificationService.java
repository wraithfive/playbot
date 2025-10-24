package com.discordbot.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for broadcasting real-time events to connected WebSocket clients.
 * Used to notify the frontend when the bot joins or leaves a guild.
 */
@Service
public class WebSocketNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationService.class);
    
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Notify all connected clients that the bot has joined a guild.
     * Clients should refresh their server list.
     */
    public void notifyGuildJoined(String guildId, String guildName) {
        try {
            Map<String, String> payload = Map.of(
                "type", "GUILD_JOINED",
                "guildId", guildId,
                "guildName", guildName
            );
            messagingTemplate.convertAndSend("/topic/guild-updates", payload);
            logger.info("Broadcasted GUILD_JOINED event for guild: {} ({})", guildName, guildId);
        } catch (Exception e) {
            logger.error("Failed to broadcast GUILD_JOINED event: {}", e.getMessage());
        }
    }

    /**
     * Notify all connected clients that the bot has left a guild.
     * Clients should refresh their server list.
     */
    public void notifyGuildLeft(String guildId, String guildName) {
        try {
            Map<String, String> payload = Map.of(
                "type", "GUILD_LEFT",
                "guildId", guildId,
                "guildName", guildName
            );
            messagingTemplate.convertAndSend("/topic/guild-updates", payload);
            logger.info("Broadcasted GUILD_LEFT event for guild: {} ({})", guildName, guildId);
        } catch (Exception e) {
            logger.error("Failed to broadcast GUILD_LEFT event: {}", e.getMessage());
        }
    }

    /**
     * Notify all connected clients that roles have been modified in a guild.
     * Clients should refresh the role list for this guild.
     */
    public void notifyRolesChanged(String guildId, String action) {
        try {
            Map<String, String> payload = Map.of(
                "type", "ROLES_CHANGED",
                "guildId", guildId,
                "action", action // e.g., "created", "deleted", "updated"
            );
            messagingTemplate.convertAndSend("/topic/guild-updates", payload);
            logger.info("Broadcasted ROLES_CHANGED event for guild: {} (action: {})", guildId, action);
        } catch (Exception e) {
            logger.error("Failed to broadcast ROLES_CHANGED event: {}", e.getMessage());
        }
    }

    /**
     * Notify all connected clients that QOTD questions have been modified in a channel.
     * Clients should refresh the question list for this channel.
     */
    public void notifyQotdQuestionsChanged(String guildId, String channelId, String action) {
        try {
            Map<String, String> payload = Map.of(
                "type", "QOTD_QUESTIONS_CHANGED",
                "guildId", guildId,
                "channelId", channelId,
                "action", action // e.g., "added", "deleted", "uploaded"
            );
            messagingTemplate.convertAndSend("/topic/guild-updates", payload);
            logger.info("Broadcasted QOTD_QUESTIONS_CHANGED event for guild: {} channel: {} (action: {})", 
                guildId, channelId, action);
        } catch (Exception e) {
            logger.error("Failed to broadcast QOTD_QUESTIONS_CHANGED event: {}", e.getMessage());
        }
    }

    /**
     * Notify all connected clients that QOTD submissions have been modified.
     * Clients should refresh the submissions list for this guild.
     */
    public void notifyQotdSubmissionsChanged(String guildId, String action) {
        try {
            Map<String, String> payload = Map.of(
                "type", "QOTD_SUBMISSIONS_CHANGED",
                "guildId", guildId,
                "action", action // e.g., "submitted", "approved", "rejected"
            );
            messagingTemplate.convertAndSend("/topic/guild-updates", payload);
            logger.info("Broadcasted QOTD_SUBMISSIONS_CHANGED event for guild: {} (action: {})", guildId, action);
        } catch (Exception e) {
            logger.error("Failed to broadcast QOTD_SUBMISSIONS_CHANGED event: {}", e.getMessage());
        }
    }
}
