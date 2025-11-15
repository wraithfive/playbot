package com.discordbot.battle.listener;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.service.ChatXpService;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Listens to Discord messages and awards XP for chat participation.
 * Primary progression system for the battle system.
 */
@Component
public class ChatXpListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ChatXpListener.class);
    private static final String LEVEL_UP_EMOJI = "⭐";

    private final BattleProperties battleProperties;
    private final ChatXpService chatXpService;

    public ChatXpListener(BattleProperties battleProperties,
                         ChatXpService chatXpService) {
        this.battleProperties = battleProperties;
        this.chatXpService = chatXpService;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Ignore bot messages (prevent XP farming with bot spam)
        if (event.getAuthor().isBot()) {
            return;
        }

        // Only process guild messages (not DMs)
        if (!event.isFromGuild()) {
            return;
        }

        // Skip if battle system is disabled
        if (!battleProperties.isEnabled()) {
            return;
        }

        String userId = event.getAuthor().getId();
        String guildId = event.getGuild().getId();

        try {
            ChatXpService.XpAwardResult result = chatXpService.awardChatXp(userId, guildId);

            // React with star emoji if user leveled up and notifications are enabled
            if (result.leveledUp() && battleProperties.getProgression().getChatXp().isLevelUpNotification()) {
                event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode(LEVEL_UP_EMOJI))
                    .queue(
                        success -> logger.debug("Level-up reaction added for user {} (level {})", userId, result.newLevel()),
                        error -> logger.warn("Failed to add level-up reaction: {}", error.getMessage())
                    );
            }

        } catch (Exception e) {
            // Silently catch exceptions to prevent message processing failures
            // Chat XP is a non-critical feature
            logger.error("Error awarding chat XP to user {} in guild {}: {}", userId, guildId, e.getMessage(), e);
        }
    }
}
