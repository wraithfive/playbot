package com.discordbot.web.dto;

/**
 * Data Transfer Object representing a Discord role for mention purposes.
 *
 * <p>This DTO is used to provide role information to the frontend for
 * autocomplete/dropdown selection when configuring mentions in QOTD streams.</p>
 *
 * @param id The Discord role's unique identifier (snowflake ID)
 * @param name The role's display name
 * @param colorRaw The role's color as a raw integer (RGB)
 * @param position The role's position in the guild's role hierarchy (higher = more privilege)
 */
public record DiscordRoleDto(
    String id,
    String name,
    int colorRaw,
    int position
) {}
