package com.discordbot.web.dto;

import java.util.List;

/**
 * Result of bulk role deletion operation
 *
 * @param successCount Number of roles successfully deleted
 * @param failureCount Number of roles that failed to delete
 * @param deletedRoles List of successfully deleted roles
 * @param errors List of errors encountered
 */
public record BulkRoleDeletionResult(
        int successCount,
        int failureCount,
        List<RoleDeletionResult> deletedRoles,
        List<String> errors
) {}
