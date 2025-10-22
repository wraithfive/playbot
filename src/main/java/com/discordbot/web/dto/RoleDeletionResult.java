package com.discordbot.web.dto;

/**
 * Result of a single role deletion operation
 *
 * @param roleId ID of the role that was deleted
 * @param roleName Name of the role that was deleted
 * @param success Whether the deletion was successful
 * @param error Error message if deletion failed
 */
public record RoleDeletionResult(
        String roleId,
        String roleName,
        boolean success,
        String error
) {}
