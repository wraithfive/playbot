package com.discordbot.web.dto;

/**
 * Data Transfer Object representing a Discord guild member for mention purposes.
 *
 * <p>This DTO is used to provide member information to the frontend for
 * autocomplete/dropdown selection when configuring mentions in QOTD streams.</p>
 *
 * @param id The Discord user's unique identifier (snowflake ID)
 * @param username The user's Discord username
 * @param displayName The user's display name (nickname) in the guild, or username if no nickname
 * @param avatarUrl The user's avatar URL, or null if no avatar
 */
public record DiscordMemberDto(
    String id,
    String username,
    String displayName,
    String avatarUrl
) {}
