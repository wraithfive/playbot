package com.discordbot.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request to delete multiple roles
 *
 * @param roleIds List of role IDs to delete (max 100 at once)
 */
public record BulkRoleDeletionRequest(
        @NotEmpty(message = "Role IDs list cannot be empty")
        @Size(max = 100, message = "Cannot delete more than 100 roles at once")
        List<String> roleIds
) {}
