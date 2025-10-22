package com.discordbot.web.dto;

/**
 * Information about the bot's role hierarchy relative to gacha roles
 *
 * @param isValid Whether the bot's role is positioned correctly (above all gacha roles)
 * @param botRoleName Name of the bot's role
 * @param botRolePosition Position of the bot's role in the hierarchy
 * @param highestGachaRolePosition Position of the highest gacha role
 * @param conflictingRoles Names of gacha roles that are above the bot's role
 */
public record RoleHierarchyStatus(
        boolean isValid,
        String botRoleName,
        int botRolePosition,
        int highestGachaRolePosition,
        java.util.List<String> conflictingRoles
) {}
