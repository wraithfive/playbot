package com.discordbot.web.controller;

import com.discordbot.web.dto.BulkRoleCreationResult;
import com.discordbot.web.dto.CreateRoleRequest;
import com.discordbot.web.dto.GachaRoleInfo;
import com.discordbot.web.service.AdminService;
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

    public RoleController(AdminService adminService) {
        this.adminService = adminService;
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

        logger.info("Initializing default roles for guild: {}", guildId);
        BulkRoleCreationResult result = adminService.initializeDefaultRoles(guildId);

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/servers/{guildId}/roles/upload-csv
     * Upload CSV file to create bulk roles
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

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        logger.info("Processing CSV upload for guild: {} (filename: {})", guildId, file.getOriginalFilename());

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

                    requests.add(new CreateRoleRequest(name, rarity, colorHex));
                }
            }
        }

        logger.info("Parsed {} roles from CSV", requests.size());
        return requests;
    }
}
