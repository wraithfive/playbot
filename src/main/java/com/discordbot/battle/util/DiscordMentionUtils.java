package com.discordbot.battle.util;

import net.dv8tion.jda.api.entities.User;

/** Utility helpers for Discord user mentions. */
public final class DiscordMentionUtils {
    private DiscordMentionUtils() {}
    public static String mention(String userId) { return "<@" + userId + ">"; }
    public static String mention(User user) { return mention(user.getId()); }
}
