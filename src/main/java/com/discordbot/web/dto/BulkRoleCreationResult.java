package com.discordbot.web.dto;

import java.util.List;

/**
 * Result DTO for bulk role creation operations.
 *
 * @param successCount Number of roles successfully created
 * @param skippedCount Number of roles skipped (duplicates)
 * @param failureCount Number of roles that failed to create
 * @param createdRoles List of successfully created role information
 * @param skippedRoles List of role names that were skipped (already exist)
 * @param errors List of error messages for failed roles
 */
public record BulkRoleCreationResult(
    int successCount,
    int skippedCount,
    int failureCount,
    List<GachaRoleInfo> createdRoles,
    List<String> skippedRoles,
    List<String> errors
) {}
