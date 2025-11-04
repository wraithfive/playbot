package com.discordbot.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a single gacha role.
 *
 * @param name The display name of the role (without "gatcha:" prefix)
 * @param rarity The rarity tier (legendary, epic, rare, uncommon, common)
 * @param colorHex The primary hex color code (e.g., "#FF5733")
 * @param secondaryColorHex Optional secondary color for gradient (e.g., "#00FF00")
 * @param tertiaryColorHex Optional tertiary color for holographic effect (forces holographic when provided)
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
    String colorHex,
    
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", 
             message = "Secondary color must be a valid hex code (e.g., #00FF00)")
    String secondaryColorHex,
    
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", 
             message = "Tertiary color must be a valid hex code (e.g., #0000FF)")
    String tertiaryColorHex
) {}
