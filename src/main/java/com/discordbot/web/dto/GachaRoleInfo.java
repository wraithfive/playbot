package com.discordbot.web.dto;

/**
 * Data Transfer Object representing a Color Gacha role.
 *
 * <p>This record encapsulates information about a Discord role that is part of
 * the Color Gacha system. Gacha roles are identified by having a name that starts
 * with the "gacha:" prefix. These roles are awarded to users when they use the
 * {@code !roll} command and provide different color rewards based on rarity.</p>
 *
 * <p>Role naming convention: {@code gacha:<displayName> (<rarity>)}</p>
 * <ul>
 *   <li>Example: "gacha:Ruby (legendary)" → displayName="Ruby", rarity="legendary"</li>
 *   <li>Example: "gacha:Emerald (rare)" → displayName="Emerald", rarity="rare"</li>
 * </ul>
 *
 * <p>Using a Java record provides:</p>
 * <ul>
 *   <li>Immutability - all fields are final</li>
 *   <li>Automatic implementations of {@code equals()}, {@code hashCode()}, and {@code toString()}</li>
 *   <li>Accessor methods: {@code id()}, {@code fullName()}, {@code displayName()}, {@code rarity()}, {@code colorHex()}, {@code position()}</li>
 *   <li>Compact syntax with less boilerplate than traditional classes</li>
 * </ul>
 *
 * @param id The Discord role's unique identifier (snowflake ID)
 * @param fullName The full role name as it appears in Discord (e.g., "gacha:Ruby (legendary)")
 * @param displayName The parsed display name without prefix and rarity (e.g., "Ruby")
 * @param rarity The rarity tier of this role (e.g., "common", "rare", "legendary")
 * @param colorHex The role color as a hex string (e.g., "#FF0000"), or null if no color is set
 * @param position The role's position in the guild's role hierarchy (higher = more privilege)
 *
 * @since 1.0.0
 * @see com.discordbot.ColorGachaHandler
 * @see <a href="https://discord.com/developers/docs/topics/permissions#role-object">Discord Role Object</a>
 */
public record GachaRoleInfo(
    String id,
    String fullName,
    String displayName,
    String rarity,
    String colorHex,
    int position
) {}
