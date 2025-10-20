package com.discordbot.web.dto;

/**
 * Request DTO for creating a single gacha role.
 *
 * @param name The display name of the role (without "gatcha:" prefix)
 * @param rarity The rarity tier (legendary, epic, rare, uncommon, common)
 * @param colorHex The hex color code (e.g., "#FF5733")
 */
public record CreateRoleRequest(
    String name,
    String rarity,
    String colorHex
) {}
