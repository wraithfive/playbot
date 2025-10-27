package com.discordbot.web.controller;

import com.discordbot.web.dto.qotd.QotdDtos.*;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.QotdStreamService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for managing QOTD streams.
 * Streams allow multiple independent QOTD configurations per channel.
 */
@RestController
@RequestMapping("/api/servers/{guildId}/channels/{channelId}/qotd/streams")
public class QotdStreamController {

    private final QotdStreamService streamService;
    private final AdminService adminService;

    public QotdStreamController(QotdStreamService streamService, AdminService adminService) {
        this.streamService = streamService;
        this.adminService = adminService;
    }

    private boolean canManage(String guildId, Authentication authentication) {
        return authentication != null && adminService.canManageGuild(authentication, guildId);
    }

    // ==================== Stream Management ====================

    /**
     * List all streams for a channel.
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/streams
     */
    @GetMapping
    public ResponseEntity<List<QotdStreamDto>> listStreams(
            @PathVariable String guildId,
            @PathVariable String channelId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(streamService.listStreams(guildId, channelId));
    }

    /**
     * Get a single stream by ID.
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}
     */
    @GetMapping("/{streamId}")
    public ResponseEntity<QotdStreamDto> getStream(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        try {
            return ResponseEntity.ok(streamService.getStream(guildId, streamId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create a new stream in a channel (max 5 per channel).
     * POST /api/servers/{guildId}/channels/{channelId}/qotd/streams
     */
    @PostMapping
    public ResponseEntity<?> createStream(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @RequestBody CreateStreamRequest request,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        try {
            QotdStreamDto created = streamService.createStream(guildId, channelId, request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Update an existing stream.
     * PUT /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}
     */
    @PutMapping("/{streamId}")
    public ResponseEntity<?> updateStream(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @RequestBody UpdateStreamRequest request,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        try {
            QotdStreamDto updated = streamService.updateStream(guildId, streamId, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Delete a stream and all its questions (cascade).
     * DELETE /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}
     */
    @DeleteMapping("/{streamId}")
    public ResponseEntity<Void> deleteStream(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        try {
            streamService.deleteStream(guildId, streamId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== Question Management ====================

    /**
     * List all questions for a stream.
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/questions
     */
    @GetMapping("/{streamId}/questions")
    public ResponseEntity<List<QotdQuestionDto>> listQuestions(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(streamService.listQuestions(guildId, streamId));
    }

    /**
     * Add a single question to a stream.
     * POST /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/questions
     */
    @PostMapping("/{streamId}/questions")
    public ResponseEntity<?> addQuestion(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @RequestBody UpsertQuestionRequest request,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        try {
            QotdQuestionDto created = streamService.addQuestion(guildId, streamId, request.text());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Delete a question from a stream.
     * DELETE /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/questions/{questionId}
     */
    @DeleteMapping("/{streamId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @PathVariable Long questionId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        streamService.deleteQuestion(guildId, streamId, questionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder questions in a stream (drag-and-drop).
     * PUT /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/questions/reorder
     */
    @PutMapping("/{streamId}/questions/reorder")
    public ResponseEntity<Void> reorderQuestions(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @RequestBody ReorderQuestionsRequest request,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        streamService.reorderQuestions(guildId, streamId, request.orderedIds());
        return ResponseEntity.ok().build();
    }

    /**
     * Upload CSV of questions to a stream.
     * POST /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/upload-csv
     */
    @PostMapping("/{streamId}/upload-csv")
    public ResponseEntity<UploadCsvResult> uploadCsv(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        try {
            UploadCsvResult result = streamService.uploadCsv(guildId, streamId, file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== Banner Management ====================

    /**
     * Get banner text for a stream.
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/banner
     */
    @GetMapping("/{streamId}/banner")
    public ResponseEntity<String> getBanner(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(streamService.getBanner(guildId, streamId));
    }

    /**
     * Set banner text for a stream.
     * PUT /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/banner
     */
    @PutMapping(value = "/{streamId}/banner", consumes = "text/plain")
    public ResponseEntity<Void> setBanner(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @RequestBody String bannerText,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        streamService.setBanner(guildId, streamId, bannerText);
        return ResponseEntity.ok().build();
    }

    /**
     * Get banner embed color for a stream.
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/banner/color
     */
    @GetMapping("/{streamId}/banner/color")
    public ResponseEntity<Integer> getBannerColor(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        Integer color = streamService.getBannerColor(guildId, streamId);
        return color != null ? ResponseEntity.ok(color) : ResponseEntity.noContent().build();
    }

    /**
     * Set banner embed color for a stream.
     * PUT /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/banner/color
     */
    @PutMapping("/{streamId}/banner/color")
    public ResponseEntity<Void> setBannerColor(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @RequestBody Integer color,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        streamService.setBannerColor(guildId, streamId, color);
        return ResponseEntity.ok().build();
    }

    /**
     * Get mention target for a stream.
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/banner/mention
     */
    @GetMapping("/{streamId}/banner/mention")
    public ResponseEntity<String> getBannerMention(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        String mention = streamService.getBannerMention(guildId, streamId);
        return mention != null ? ResponseEntity.ok(mention) : ResponseEntity.noContent().build();
    }

    /**
     * Set mention target for a stream.
     * PUT /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/banner/mention
     */
    @PutMapping(value = "/{streamId}/banner/mention", consumes = "text/plain")
    public ResponseEntity<Void> setBannerMention(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            @RequestBody(required = false) String mention,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        // Normalize empty/null to null
        String normalized = (mention == null || mention.trim().isEmpty()) ? null : mention.trim();

        streamService.setBannerMention(guildId, streamId, normalized);
        return ResponseEntity.ok().build();
    }

    /**
     * Reset banner to defaults.
     * POST /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/banner/reset
     */
    @PostMapping("/{streamId}/banner/reset")
    public ResponseEntity<Void> resetBanner(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        streamService.resetBanner(guildId, streamId);
        return ResponseEntity.ok().build();
    }

    // ==================== Manual Posting ====================

    /**
     * Manually trigger posting for a stream.
     * POST /api/servers/{guildId}/channels/{channelId}/qotd/streams/{streamId}/post-now
     */
    @PostMapping("/{streamId}/post-now")
    public ResponseEntity<Void> postNow(
            @PathVariable String guildId,
            @PathVariable Long streamId,
            Authentication authentication) {

        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }

        try {
            streamService.postNextQuestion(guildId, streamId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
