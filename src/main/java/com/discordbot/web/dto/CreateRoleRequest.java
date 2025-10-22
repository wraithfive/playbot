package com.discordbot.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a single gacha role.
 *
 * @param name The display name of the role (without "gatcha:" prefix)
 * @param rarity The rarity tier (legendary, epic, rare, uncommon, common)
 * @param colorHex The hex color code (e.g., "#FF5733")
 */
public record CreateRoleRequest(
    @NotBlank(message = "Role name is required")
    @Size(min = 1, max = 100, message = "Role name must be between 1 and 100 characters")
    String name,
    
    @NotBlank(message = "Rarity is required")
    @Pattern(regexp = "^(legendary|epic|rare|uncommon|common)$", 
             message = "Rarity must be one of: legendary, epic, rare, uncommon, common")
    String rarity,
    
    @NotBlank(message = "Color hex code is required")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", 
             message = "Color must be a valid hex code (e.g., #FF5733)")
    String colorHex
) {}
