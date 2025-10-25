package com.discordbot.web.controller;

import com.discordbot.web.dto.qotd.QotdDtos;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.QotdService;
import com.discordbot.web.service.QotdSubmissionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/servers/{guildId}")
public class QotdController {
    /**
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/banner
     * Fetch QOTD banner for a channel
     */
    @GetMapping("/channels/{channelId}/qotd/banner")
    public ResponseEntity<String> getBanner(
            @PathVariable String guildId,
            @PathVariable String channelId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        return ResponseEntity.ok(qotdService.getBanner(channelId));
    }

    /**
     * PUT /api/servers/{guildId}/channels/{channelId}/qotd/banner
     * Update QOTD banner for a channel
     */
    @PutMapping("/channels/{channelId}/qotd/banner")
    public ResponseEntity<?> setBanner(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @RequestBody String bannerText,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        
        // Validate banner length (DB VARCHAR(160))
        if (bannerText != null && bannerText.length() > 160) {
            return ResponseEntity.badRequest()
                    .body("Banner text exceeds maximum length of 160 characters. Current length: " + bannerText.length());
        }
        
        qotdService.setBanner(channelId, bannerText);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/servers/{guildId}/channels/{channelId}/qotd/banner/color
     * Fetch QOTD banner embed color for a channel
     */
    @GetMapping("/channels/{channelId}/qotd/banner/color")
    public ResponseEntity<Integer> getBannerColor(
            @PathVariable String guildId,
            @PathVariable String channelId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        Integer color = qotdService.getBannerColor(channelId);
        return ResponseEntity.ok(color);
    }

    /**
     * PUT /api/servers/{guildId}/channels/{channelId}/qotd/banner/color
     * Update QOTD banner embed color for a channel
     */
    @PutMapping("/channels/{channelId}/qotd/banner/color")
    public ResponseEntity<?> setBannerColor(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @RequestBody Integer color,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        qotdService.setBannerColor(channelId, color);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/servers/{guildId}/channels/{channelId}/qotd/banner/reset
     * Reset QOTD banner to default for a channel
     */
    @PostMapping("/channels/{channelId}/qotd/banner/reset")
    public ResponseEntity<?> resetBanner(
            @PathVariable String guildId,
            @PathVariable String channelId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        qotdService.resetBanner(channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/servers/{guildId}/qotd/download-example
     * Download example QOTD CSV file
     */
    @GetMapping("/qotd/download-example")
    public ResponseEntity<org.springframework.core.io.Resource> downloadExampleQotdCsv(
            @PathVariable String guildId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) {
            return ResponseEntity.status(403).build();
        }
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("example-qotd.csv");
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=example-qotd.csv")
                    .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                    .body(resource);
        } catch (Exception e) {
            logger.error("Failed to load example QOTD CSV", e);
            return ResponseEntity.status(500).build();
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(QotdController.class);

    private final QotdService qotdService;
    private final QotdSubmissionService submissionService;
    private final AdminService adminService;

    public QotdController(QotdService qotdService, QotdSubmissionService submissionService, AdminService adminService) {
        this.qotdService = qotdService;
        this.submissionService = submissionService;
        this.adminService = adminService;
    }

    private boolean canManage(String guildId, Authentication authentication) {
        return authentication != null && adminService.canManageGuild(authentication, guildId);
    }

    // Guild-level endpoints
    @GetMapping("/qotd/channels")
    public ResponseEntity<List<QotdDtos.TextChannelInfo>> listChannels(@PathVariable String guildId, Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(qotdService.listTextChannels(guildId));
    }

    @GetMapping("/qotd/configs")
    public ResponseEntity<List<QotdDtos.QotdConfigDto>> listConfigs(@PathVariable String guildId, Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(qotdService.listGuildConfigs(guildId));
    }

    // Per-channel endpoints
    @GetMapping("/channels/{channelId}/qotd/config")
    public ResponseEntity<QotdDtos.QotdConfigDto> getConfig(
            @PathVariable String guildId,
            @PathVariable String channelId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        return ResponseEntity.ok(qotdService.getConfig(guildId, channelId));
    }

    @PutMapping("/channels/{channelId}/qotd/config")
    public ResponseEntity<QotdDtos.QotdConfigDto> updateConfig(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @Valid @RequestBody QotdDtos.UpdateConfigRequest request,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        return ResponseEntity.ok(qotdService.updateConfig(guildId, channelId, request));
    }

    @GetMapping("/channels/{channelId}/qotd/questions")
    public ResponseEntity<List<QotdDtos.QotdQuestionDto>> getQuestions(
            @PathVariable String guildId,
            @PathVariable String channelId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        return ResponseEntity.ok(qotdService.listQuestions(guildId, channelId));
    }

    @PostMapping("/channels/{channelId}/qotd/questions")
    public ResponseEntity<QotdDtos.QotdQuestionDto> addQuestion(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @Valid @RequestBody QotdDtos.UpsertQuestionRequest request,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        return ResponseEntity.ok(qotdService.addQuestion(guildId, channelId, request.text()));
    }

    @DeleteMapping("/channels/{channelId}/qotd/questions/{id}")
    public ResponseEntity<?> deleteQuestion(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @PathVariable Long id,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        qotdService.deleteQuestion(guildId, channelId, id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/channels/{channelId}/qotd/questions/reorder")
    public ResponseEntity<?> reorderQuestions(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @Valid @RequestBody QotdDtos.ReorderQuestionsRequest request,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        qotdService.reorderQuestions(guildId, channelId, request.orderedIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/channels/{channelId}/qotd/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QotdDtos.UploadCsvResult> uploadCsv(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok(qotdService.uploadCsv(guildId, channelId, content));
        } catch (Exception e) {
            logger.error("Failed to process QOTD CSV: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/channels/{channelId}/qotd/post-now")
    public ResponseEntity<?> postNow(
            @PathVariable String guildId,
            @PathVariable String channelId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        boolean ok = qotdService.postNextQuestion(guildId, channelId);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    // Submissions API (guild-wide, but approval targets a specific channel)
    @GetMapping("/qotd/submissions")
    public ResponseEntity<List<QotdDtos.QotdSubmissionDto>> listPending(
            @PathVariable String guildId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(submissionService.listPending(guildId));
    }

    @PostMapping("/channels/{channelId}/qotd/submissions/{id}/approve")
    public ResponseEntity<QotdDtos.QotdSubmissionDto> approve(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @PathVariable Long id,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        String approverId = authentication.getName();
        String approverUsername = authentication.getName();
        return ResponseEntity.ok(submissionService.approve(guildId, channelId, id, approverId, approverUsername));
    }

    @PostMapping("/qotd/submissions/{id}/reject")
    public ResponseEntity<QotdDtos.QotdSubmissionDto> reject(
            @PathVariable String guildId,
            @PathVariable Long id,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        String approverId = authentication.getName();
        String approverUsername = authentication.getName();
        return ResponseEntity.ok(submissionService.reject(guildId, id, approverId, approverUsername));
    }

    @PostMapping("/channels/{channelId}/qotd/submissions/bulk-approve")
    public ResponseEntity<QotdDtos.BulkActionResult> bulkApprove(
            @PathVariable String guildId,
            @PathVariable String channelId,
            @RequestBody QotdDtos.BulkIdsRequest req,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        qotdService.validateChannelBelongsToGuild(guildId, channelId);
        String approverId = authentication.getName();
        String approverUsername = authentication.getName();
        return ResponseEntity.ok(submissionService.approveBulk(guildId, channelId, req.ids(), approverId, approverUsername));
    }

    @PostMapping("/qotd/submissions/bulk-reject")
    public ResponseEntity<QotdDtos.BulkActionResult> bulkReject(
            @PathVariable String guildId,
            @RequestBody QotdDtos.BulkIdsRequest req,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        String approverId = authentication.getName();
        String approverUsername = authentication.getName();
        return ResponseEntity.ok(submissionService.rejectBulk(guildId, req.ids(), approverId, approverUsername));
    }
}
