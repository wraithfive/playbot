package com.discordbot.web.dto;

import java.util.List;

/**
 * Result DTO for bulk role creation operations.
 *
 * @param successCount Number of roles successfully created
 * @param failureCount Number of roles that failed to create
 * @param createdRoles List of successfully created role information
 * @param errors List of error messages for failed roles
 */
public record BulkRoleCreationResult(
    int successCount,
    int failureCount,
    List<GachaRoleInfo> createdRoles,
    List<String> errors
) {}
