package com.discordbot.web.dto;

/**
 * Data Transfer Object representing Discord user information.
 *
 * <p>This record encapsulates the essential information about a Discord user
 * retrieved from the Discord OAuth2 API. It is used to transfer user data
 * between the OAuth2 authentication layer and the web API controllers.</p>
 *
 * <p>Using a Java record provides:</p>
 * <ul>
 *   <li>Immutability - all fields are final</li>
 *   <li>Automatic implementations of {@code equals()}, {@code hashCode()}, and {@code toString()}</li>
 *   <li>Compact syntax with less boilerplate than traditional classes</li>
 *   <li>Thread-safe value semantics</li>
 * </ul>
 *
 * @param id The Discord user's unique identifier (snowflake ID)
 * @param username The user's current username (without discriminator)
 * @param discriminator The user's 4-digit discriminator (may be "0" for newer accounts with unique usernames)
 * @param avatar The user's avatar hash, used to construct the avatar URL. May be null if user has no custom avatar.
 *
 * @since 1.0.0
 * @see <a href="https://discord.com/developers/docs/resources/user">Discord User Resource</a>
 */
public record UserInfo(
    String id,
    String username,
    String discriminator,
    String avatar
) {}
