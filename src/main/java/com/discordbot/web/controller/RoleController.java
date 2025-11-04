package com.discordbot.web.controller;

import com.discordbot.web.dto.BulkRoleCreationResult;
import com.discordbot.web.dto.BulkRoleDeletionRequest;
import com.discordbot.web.dto.BulkRoleDeletionResult;
import com.discordbot.web.dto.CreateRoleRequest;
import com.discordbot.web.dto.GachaRoleInfo;
import com.discordbot.web.dto.RoleDeletionResult;
import com.discordbot.web.dto.RoleHierarchyStatus;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.RateLimitService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/servers/{guildId}/roles")
public class RoleController {

    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);

    private final AdminService adminService;
    private final RateLimitService rateLimitService;

    public RoleController(AdminService adminService, RateLimitService rateLimitService) {
        this.adminService = adminService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * POST /api/servers/{guildId}/roles
     * Create a single gatcha role
     */
    @PostMapping
    public ResponseEntity<GachaRoleInfo> createRole(
            @PathVariable String guildId,
            @jakarta.validation.Valid @RequestBody CreateRoleRequest request,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        if (!adminService.canManageGuild(authentication, guildId)) {
            return ResponseEntity.status(403).build();
        }

        try {
            GachaRoleInfo created = adminService.createGatchaRole(guildId, request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            logger.error("Failed to create role for guild {}: {}", guildId, ex.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/servers/{guildId}/roles
     * Returns all gatcha roles for a specific server
     * SECURED: Requires OAuth2 authentication + user must be admin in that server
     */
    @GetMapping
    public ResponseEntity<List<GachaRoleInfo>> getGatchaRoles(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            logger.warn("Unauthenticated request to /api/servers/{}/roles", guildId);
            return ResponseEntity.status(401).build();
        }

        // Check if user has permission to manage this guild
        if (!adminService.canManageGuild(authentication, guildId)) {
            logger.warn("Unauthorized access attempt to roles for guild: {}", guildId);
            return ResponseEntity.status(403).build();
        }

        logger.info("Fetching gatcha roles for guild: {}", guildId);
        List<GachaRoleInfo> roles = adminService.getGatchaRoles(guildId);

        return ResponseEntity.ok(roles);
    }

    /**
     * GET /api/servers/{guildId}/roles/hierarchy-check
     * Check if bot's role is positioned correctly above gacha roles
     */
    @GetMapping("/hierarchy-check")
    public ResponseEntity<RoleHierarchyStatus> checkRoleHierarchy(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        if (!adminService.canManageGuild(authentication, guildId)) {
            return ResponseEntity.status(403).build();
        }

        logger.info("Checking role hierarchy for guild: {}", guildId);
        RoleHierarchyStatus status = adminService.checkRoleHierarchy(guildId);

        return ResponseEntity.ok(status);
    }

    /**
     * POST /api/servers/{guildId}/roles/init-defaults
     * Initialize default gatcha roles
     */
    @PostMapping("/init-defaults")
    public ResponseEntity<BulkRoleCreationResult> initializeDefaultRoles(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        if (!adminService.canManageGuild(authentication, guildId)) {
            return ResponseEntity.status(403).build();
        }

        // Rate limiting check for bulk operations
        if (!rateLimitService.allowBulkOperation(authentication)) {
            logger.warn("Rate limit exceeded for init-defaults: guildId={}", guildId);
            return ResponseEntity.status(429).build(); // 429 Too Many Requests
        }

        logger.info("Initializing default roles for guild: {}", guildId);
        BulkRoleCreationResult result = adminService.initializeDefaultRoles(guildId);

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/servers/{guildId}/roles/upload-csv
     * Upload CSV file to create bulk roles
     * Maximum file size: 1MB (configured via Spring property)
     */
    @PostMapping("/upload-csv")
    public ResponseEntity<BulkRoleCreationResult> uploadCsv(
            @PathVariable String guildId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        if (!adminService.canManageGuild(authentication, guildId)) {
            return ResponseEntity.status(403).build();
        }

        // Rate limiting check for bulk operations
        if (!rateLimitService.allowBulkOperation(authentication)) {
            logger.warn("Rate limit exceeded for CSV upload: guildId={}", guildId);
            return ResponseEntity.status(429).build(); // 429 Too Many Requests
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate file size (1MB max)
        long maxSize = 1024 * 1024; // 1MB
        if (file.getSize() > maxSize) {
            logger.warn("CSV file too large: {} bytes (max: {} bytes)", file.getSize(), maxSize);
            return ResponseEntity.status(413).build(); // 413 Payload Too Large
        }

        logger.info("Processing CSV upload for guild: {} (filename: {}, size: {} bytes)", 
            guildId, file.getOriginalFilename(), file.getSize());

        try {
            List<CreateRoleRequest> requests = parseCsv(file);
            BulkRoleCreationResult result = adminService.createBulkGatchaRoles(guildId, requests);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to process CSV upload", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/servers/{guildId}/roles/download-example
     * Download example CSV file
     */
    @GetMapping("/download-example")
    public ResponseEntity<Resource> downloadExampleCsv(
            @PathVariable String guildId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        if (!adminService.canManageGuild(authentication, guildId)) {
            return ResponseEntity.status(403).build();
        }

        try {
            Resource resource = new ClassPathResource("example-roles.csv");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=example-roles.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(resource);
        } catch (Exception e) {
            logger.error("Failed to load example CSV", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Parse CSV file into CreateRoleRequest objects
     */
    private List<CreateRoleRequest> parseCsv(MultipartFile file) throws Exception {
        List<CreateRoleRequest> requests = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header row
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String name = parts[0].trim();
                    String rarity = parts[1].trim();
                    String colorHex = parts[2].trim();

                    String secondary = null;
                    String tertiary = null;
                    if (parts.length >= 4) {
                        secondary = parts[3].trim();
                        if (secondary.isEmpty()) secondary = null;
                    }
                    if (parts.length >= 5) {
                        tertiary = parts[4].trim();
                        if (tertiary.isEmpty()) tertiary = null;
                    }

                    // Normalize empty primary color to null (AdminService defaults to white)
                    if (colorHex.isEmpty()) {
                        colorHex = null;
                    }

                    requests.add(new CreateRoleRequest(name, rarity, colorHex, secondary, tertiary));
                }
            }
        }

        logger.info("Parsed {} roles from CSV", requests.size());
        return requests;
    }

    /**
     * DELETE /api/servers/{guildId}/roles/{roleId}
     * Delete a single gacha role
     */
    @DeleteMapping("/{roleId}")
    public ResponseEntity<RoleDeletionResult> deleteRole(
            @PathVariable String guildId,
            @PathVariable String roleId,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        if (!adminService.canManageGuild(authentication, guildId)) {
            return ResponseEntity.status(403).build();
        }

        logger.info("Deleting role {} from guild: {}", roleId, guildId);
        RoleDeletionResult result = adminService.deleteGatchaRole(guildId, roleId);

        if (!result.success()) {
            logger.warn("Failed to delete role {}: {}", roleId, result.error());
            return ResponseEntity.status(400).body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/servers/{guildId}/roles/bulk-delete
     * Delete multiple gacha roles
     */
    @PostMapping("/bulk-delete")
    public ResponseEntity<BulkRoleDeletionResult> bulkDeleteRoles(
            @PathVariable String guildId,
            @Valid @RequestBody BulkRoleDeletionRequest request,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        if (!adminService.canManageGuild(authentication, guildId)) {
            return ResponseEntity.status(403).build();
        }

        // Rate limiting check for bulk operations
        if (!rateLimitService.allowBulkOperation(authentication)) {
            logger.warn("Rate limit exceeded for bulk delete: guildId={}", guildId);
            return ResponseEntity.status(429).build(); // 429 Too Many Requests
        }

        logger.info("Bulk deleting {} roles from guild: {}", request.roleIds().size(), guildId);
        BulkRoleDeletionResult result = adminService.deleteBulkGatchaRoles(guildId, request.roleIds());

        return ResponseEntity.ok(result);
    }
}
