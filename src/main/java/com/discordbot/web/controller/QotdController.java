package com.discordbot.web.controller;

import com.discordbot.web.dto.qotd.QotdDtos;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.QotdService;
import com.discordbot.web.service.QotdSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers/{guildId}")
public class QotdController {
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
            @RequestParam(required = false) Long streamId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        String approverId = authentication.getName();
        String approverUsername = authentication.getName();
        return ResponseEntity.ok(submissionService.approve(guildId, channelId, id, streamId, approverId, approverUsername));
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
            @RequestParam(required = false) Long streamId,
            Authentication authentication) {
        if (!canManage(guildId, authentication)) return ResponseEntity.status(403).build();
        String approverId = authentication.getName();
        String approverUsername = authentication.getName();
        return ResponseEntity.ok(submissionService.approveBulk(guildId, channelId, req.ids(), streamId, approverId, approverUsername));
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
