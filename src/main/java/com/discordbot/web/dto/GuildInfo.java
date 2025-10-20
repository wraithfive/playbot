package com.discordbot.web.dto;

/**
 * Data Transfer Object representing Discord guild (server) information.
 *
 * <p>This record encapsulates information about a Discord guild along with
 * metadata about the authenticated user's permissions and the bot's presence
 * in that guild. It is used by the web admin panel to determine which servers
 * the user can manage.</p>
 *
 * <p>A guild is considered "manageable" if both:</p>
 * <ul>
 *   <li>The authenticated user has ADMINISTRATOR or MANAGE_SERVER permissions</li>
 *   <li>The Color Gacha Bot is present in the guild</li>
 * </ul>
 *
 * <p>Using a Java record provides:</p>
 * <ul>
 *   <li>Immutability - all fields are final</li>
 *   <li>Automatic implementations of {@code equals()}, {@code hashCode()}, and {@code toString()}</li>
 *   <li>Accessor methods: {@code id()}, {@code name()}, {@code iconUrl()}, {@code userIsAdmin()}, {@code botIsPresent()}</li>
 *   <li>Compact syntax with less boilerplate than traditional classes</li>
 * </ul>
 *
 * @param id The Discord guild's unique identifier (snowflake ID)
 * @param name The guild's display name
 * @param iconUrl The URL to the guild's icon image, or null if no icon is set
 * @param userIsAdmin Whether the authenticated user has admin/manage server permissions in this guild
 * @param botIsPresent Whether the Color Gacha Bot is currently a member of this guild
 *
 * @since 1.0.0
 * @see <a href="https://discord.com/developers/docs/resources/guild">Discord Guild Resource</a>
 */
public record GuildInfo(
    String id,
    String name,
    String iconUrl,
    boolean userIsAdmin,
    boolean botIsPresent
) {}
