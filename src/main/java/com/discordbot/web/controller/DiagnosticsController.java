package com.discordbot.web.controller;

import com.discordbot.entity.QotdStream;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.web.service.AdminService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Diagnostic endpoints for troubleshooting (admin only)
 */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private final JDA jda;
    private final AdminService adminService;
    private final QotdStreamRepository streamRepository;

    public DiagnosticsController(JDA jda, AdminService adminService, QotdStreamRepository streamRepository) {
        this.jda = jda;
        this.adminService = adminService;
        this.streamRepository = streamRepository;
    }

    /**
     * Check what threads Discord API returns for a guild
     */
    @GetMapping("/guilds/{guildId}/threads")
    public ResponseEntity<Map<String, Object>> getActiveThreads(
            @PathVariable String guildId,
            Authentication auth) {
        
        if (auth == null || !adminService.canManageGuild(auth, guildId)) {
            return ResponseEntity.status(403).build();
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Guild not found"));
        }

        try {
            List<ThreadChannel> activeThreads = guild.retrieveActiveThreads().complete();
            Map<String, Object> result = new HashMap<>();
            result.put("threadCount", activeThreads.size());
            result.put("threads", activeThreads.stream()
                .map(t -> Map.of(
                    "id", t.getId(),
                    "name", t.getName(),
                    "parent", t.getParentChannel() != null ? t.getParentChannel().getId() : "null",
                    "archived", t.isArchived(),
                    "canTalk", t.canTalk()
                ))
                .toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve threads",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Check stream status for a specific guild with detailed output
     */
    @GetMapping("/guilds/{guildId}/stream-status")
    public ResponseEntity<Map<String, Object>> getStreamStatusDiagnostics(
            @PathVariable String guildId,
            Authentication auth) {
        
        if (auth == null || !adminService.canManageGuild(auth, guildId)) {
            return ResponseEntity.status(403).build();
        }

        var status = adminService.getStreamStatusForAllChannels(guildId);
        Map<String, Object> result = new HashMap<>();
        result.put("total", status.size());
        result.put("channels", status);
        return ResponseEntity.ok(result);
    }

    /**
     * Detect orphaned or stale streams (ones pointing to channels/threads that no longer exist)
     */
    @GetMapping("/guilds/{guildId}/orphaned-streams")
    public ResponseEntity<Map<String, Object>> findOrphanedStreams(
            @PathVariable String guildId,
            Authentication auth) {
        
        if (auth == null || !adminService.canManageGuild(auth, guildId)) {
            return ResponseEntity.status(403).build();
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Guild not found"));
        }

        // Get all streams for this guild
        List<QotdStream> allStreams = streamRepository.findByGuildIdOrderByChannelIdAscIdAsc(guildId);
        
        // Get all valid channel/thread IDs from Discord
        Set<String> validIds = new HashSet<>();
        guild.getTextChannels().forEach(c -> validIds.add(c.getId()));
        guild.getNewsChannels().forEach(c -> validIds.add(c.getId()));
        guild.getForumChannels().forEach(c -> validIds.add(c.getId()));
        
        try {
            guild.retrieveActiveThreads().complete().forEach(t -> validIds.add(t.getId()));
        } catch (Exception e) {
            // Thread retrieval failed, continue with what we have
        }

        // Find orphaned streams
        List<Map<String, Object>> orphaned = new ArrayList<>();
        for (QotdStream stream : allStreams) {
            if (!validIds.contains(stream.getChannelId())) {
                orphaned.add(Map.of(
                    "id", stream.getId(),
                    "name", stream.getStreamName(),
                    "channelId", stream.getChannelId(),
                    "enabled", stream.getEnabled()
                ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalStreams", allStreams.size());
        result.put("orphanedCount", orphaned.size());
        result.put("orphaned", orphaned);
        result.put("validChannelIds", validIds.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Delete orphaned streams for a guild
     */
    @PostMapping("/guilds/{guildId}/cleanup-orphaned")
    public ResponseEntity<Map<String, Object>> cleanupOrphanedStreams(
            @PathVariable String guildId,
            Authentication auth) {
        
        if (auth == null || !adminService.canManageGuild(auth, guildId)) {
            return ResponseEntity.status(403).build();
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Guild not found"));
        }

        // Get all streams for this guild
        List<QotdStream> allStreams = streamRepository.findByGuildIdOrderByChannelIdAscIdAsc(guildId);
        
        // Get all valid channel/thread IDs from Discord
        Set<String> validIds = new HashSet<>();
        guild.getTextChannels().forEach(c -> validIds.add(c.getId()));
        guild.getNewsChannels().forEach(c -> validIds.add(c.getId()));
        guild.getForumChannels().forEach(c -> validIds.add(c.getId()));
        
        try {
            guild.retrieveActiveThreads().complete().forEach(t -> validIds.add(t.getId()));
        } catch (Exception e) {
            // Thread retrieval failed, continue with what we have
        }

        // Delete orphaned streams
        int deletedCount = 0;
        List<Long> deletedIds = new ArrayList<>();
        for (QotdStream stream : allStreams) {
            if (!validIds.contains(stream.getChannelId())) {
                streamRepository.delete(stream);
                deletedCount++;
                deletedIds.add(stream.getId());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("deletedStreamIds", deletedIds);
        result.put("message", deletedCount + " orphaned stream(s) deleted");
        return ResponseEntity.ok(result);
    }
}

