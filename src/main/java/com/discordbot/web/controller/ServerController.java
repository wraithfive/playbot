package com.discordbot.web.controller;

import com.discordbot.web.dto.GuildInfo;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelTreeNodeDto;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelStreamStatusDto;
import com.discordbot.web.dto.qotd.QotdDtos.MentionTargetDto;
import com.discordbot.web.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private static final Logger logger = LoggerFactory.getLogger(ServerController.class);

    private final AdminService adminService;

    public ServerController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * GET /api/servers
     * Returns list of servers where user is admin AND bot is present
     * SECURED: Requires OAuth2 authentication
     */
    @GetMapping
    public ResponseEntity<List<GuildInfo>> getManageableServers(Authentication authentication) {
        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers");
            return ResponseEntity.status(401).build();
        }

        logger.info("Fetching manageable servers for authenticated user");
        List<GuildInfo> guilds = adminService.getManageableGuilds(authentication);

        logger.info("Returning {} guilds", guilds.size());

        return ResponseEntity.ok(guilds);
    }

    /**
     * GET /api/servers/{guildId}/channel-options
     * Returns a tree of channels with nested threads for selection.
     * SECURED: Requires OAuth2 authentication + user must be admin in that server
     */
    @GetMapping("/{guildId}/channel-options")
    public ResponseEntity<List<ChannelTreeNodeDto>> getChannelOptions(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers/{}/channel-options", guildId);
            return ResponseEntity.status(401).build();
        }

        // Check if user has permission to manage this guild
        if (!adminService.canManageGuild(authentication, guildId)) {
            logger.warn("Unauthorized access attempt to guild channel options: {}", guildId);
            return ResponseEntity.status(403).build();
        }

        List<ChannelTreeNodeDto> options = adminService.getChannelOptions(guildId);
        return ResponseEntity.ok(options);
    }

    /**
     * GET /api/servers/{guildId}/qotd/stream-status
     * Returns which channels/threads have configured or enabled streams (batch endpoint).
     * Used to populate tree indicators without N+1 queries.
     * SECURED: Requires OAuth2 authentication + user must be admin in that server
     */
    @GetMapping("/{guildId}/qotd/stream-status")
    public ResponseEntity<List<ChannelStreamStatusDto>> getStreamStatus(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers/{}/qotd/stream-status", guildId);
            return ResponseEntity.status(401).build();
        }

        // Check if user has permission to manage this guild
        if (!adminService.canManageGuild(authentication, guildId)) {
            logger.warn("Unauthorized access attempt to guild stream status: {}", guildId);
            return ResponseEntity.status(403).build();
        }

        List<ChannelStreamStatusDto> status = adminService.getStreamStatusForAllChannels(guildId);
        return ResponseEntity.ok(status);
    }

    /**
     * GET /api/servers/{guildId}
     * Returns info about a specific server
     * SECURED: Requires OAuth2 authentication + user must be admin in that server
     */
    @GetMapping("/{guildId}")
    public ResponseEntity<GuildInfo> getServerInfo(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers/{}", guildId);
            return ResponseEntity.status(401).build();
        }

        // Check if user has permission to manage this guild
        if (!adminService.canManageGuild(authentication, guildId)) {
            logger.warn("Unauthorized access attempt to guild: {}", guildId);
            return ResponseEntity.status(403).build();
        }

        // Get guild info from manageable guilds list
        List<GuildInfo> guilds = adminService.getManageableGuilds(authentication);
        return guilds.stream()
                .filter(g -> g.id().equals(guildId))  // Java 25: record accessor
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/servers/{guildId}/invite
     * Generate bot invite URL for a specific server
     * SECURED: Requires OAuth2 authentication + user must be ACTUAL admin (not Staff role)
     * Note: Does NOT require bot to be present (that's the whole point of inviting it!)
     */
    @GetMapping("/{guildId}/invite")
    public ResponseEntity<Map<String, String>> getBotInviteUrl(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers/{}/invite", guildId);
            return ResponseEntity.status(401).build();
        }

        // Check if user has actual admin permissions (Staff role NOT allowed for bot invites)
        if (!adminService.isUserActualAdminInGuild(authentication, guildId)) {
            logger.warn("Unauthorized attempt to get bot invite for guild: {}", guildId);
            return ResponseEntity.status(403).build();
        }

        String inviteUrl = adminService.generateBotInviteUrl(guildId);
        return ResponseEntity.ok(Map.of("inviteUrl", inviteUrl));
    }

    /**
     * DELETE /api/servers/{guildId}/bot
     * Remove bot from a specific server
     * SECURED: Requires OAuth2 authentication + user must be admin in that server
     */
    @DeleteMapping("/{guildId}/bot")
    public ResponseEntity<Map<String, String>> removeBot(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            logger.warn("Unauthenticated request to remove bot from guild: {}", guildId);
            return ResponseEntity.status(401).build();
        }

        // Check if user has permission to manage this guild
        if (!adminService.canManageGuild(authentication, guildId)) {
            logger.warn("Unauthorized attempt to remove bot from guild: {}", guildId);
            return ResponseEntity.status(403).build();
        }

        try {
            adminService.leaveBotFromGuild(guildId);
            return ResponseEntity.ok(Map.of("message", "Bot removal initiated successfully"));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to remove bot from guild {}: {}", guildId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error removing bot from guild {}: {}", guildId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to remove bot"));
        }
    }

    /**
     * POST /api/servers/refresh
     * Force refresh the user's guilds cache (for instant UI update after bot add/remove)
     * SECURED: Requires OAuth2 authentication
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshGuildsCache(Authentication authentication) {
        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers/refresh");
            return ResponseEntity.status(401).build();
        }
        adminService.evictGuildsCache(authentication);
        logger.info("Force-refreshed guilds cache for user");
        return ResponseEntity.ok(Map.of("message", "Guilds cache cleared"));
    }

    /**
     * GET /api/servers/{guildId}/mention-targets
     * Returns all mentionable targets (roles and special mentions) for QOTD autocomplete.
     * SECURED: Requires OAuth2 authentication + user must be admin in that server
     */
    @GetMapping("/{guildId}/mention-targets")
    public ResponseEntity<List<MentionTargetDto>> getMentionTargets(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers/{}/mention-targets", guildId);
            return ResponseEntity.status(401).build();
        }

        // Check if user has permission to manage this guild
        if (!adminService.canManageGuild(authentication, guildId)) {
            logger.warn("Unauthorized access attempt to guild mention targets: {}", guildId);
            return ResponseEntity.status(403).build();
        }

        List<MentionTargetDto> targets = adminService.getMentionableTargets(guildId);
        return ResponseEntity.ok(targets);
    }
}
