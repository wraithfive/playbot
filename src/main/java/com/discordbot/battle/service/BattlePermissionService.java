package com.discordbot.battle.service;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Permission validation service for battle system admin operations.
 * Phase 11: Security & Permissions
 */
@Service
public class BattlePermissionService {

    private static final Logger logger = LoggerFactory.getLogger(BattlePermissionService.class);

    /**
     * Check if a user has administrator permissions in a guild.
     * Admin permissions are: ADMINISTRATOR or MANAGE_SERVER
     *
     * @param guild the guild to check permissions in
     * @param userId the user ID to check
     * @return true if user has admin permissions, false otherwise
     */
    public boolean hasAdminPermission(Guild guild, String userId) {
        if (guild == null) {
            logger.warn("Cannot check admin permission: guild is null");
            return false;
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
            logger.warn("Cannot check admin permission: user {} not found in guild {}", userId, guild.getId());
            return false;
        }

        boolean hasPermission = member.hasPermission(Permission.ADMINISTRATOR) ||
                               member.hasPermission(Permission.MANAGE_SERVER);

        if (!hasPermission) {
            logger.warn("User {} attempted admin operation in guild {} without required permissions",
                userId, guild.getId());
        }

        return hasPermission;
    }

    /**
     * Get a user-friendly error message for permission denial.
     *
     * @return error message string
     */
    public String getPermissionDeniedMessage() {
        return "❌ You do not have permission to use this command. " +
               "Requires **Administrator** or **Manage Server** permission.";
    }
}
