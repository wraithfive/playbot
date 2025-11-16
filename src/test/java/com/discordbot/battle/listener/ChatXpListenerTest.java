package com.discordbot.battle.listener;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.service.ChatXpService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatXpListener.
 * Tests chat message XP award events.
 */
class ChatXpListenerTest {

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private BattleProperties.ProgressionConfig progressionConfig;

    @Mock
    private BattleProperties.ProgressionConfig.ChatXpConfig chatXpConfig;

    @Mock
    private ChatXpService chatXpService;

    @Mock
    private MessageReceivedEvent event;

    @Mock
    private User author;

    @Mock
    private Guild guild;

    @Mock
    private Message message;

    @Mock
    private AuditableRestAction<Void> reactionAction;

    private ChatXpListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        when(battleProperties.getProgression()).thenReturn(progressionConfig);
        when(progressionConfig.getChatXp()).thenReturn(chatXpConfig);
        when(chatXpConfig.isLevelUpNotification()).thenReturn(true);

        listener = new ChatXpListener(battleProperties, chatXpService);

        // Default mocks
        when(event.getAuthor()).thenReturn(author);
        when(event.getMessage()).thenReturn(message);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(author.isBot()).thenReturn(false);
        when(author.getId()).thenReturn("user1");
        when(guild.getId()).thenReturn("guild1");
    }

    @Test
    void onMessageReceived_ignoresBotMessages() {
        // Given: Message is from a bot
        when(author.isBot()).thenReturn(true);

        // When: Message received
        listener.onMessageReceived(event);

        // Then: No XP is awarded
        verify(chatXpService, never()).awardChatXp(any(), any());
    }

    @Test
    void onMessageReceived_ignoresDMs() {
        // Given: Message is a DM (not from guild)
        when(event.isFromGuild()).thenReturn(false);

        // When: Message received
        listener.onMessageReceived(event);

        // Then: No XP is awarded
        verify(chatXpService, never()).awardChatXp(any(), any());
    }

    @Test
    void onMessageReceived_skipsWhenBattleSystemDisabled() {
        // Given: Battle system is disabled
        when(battleProperties.isEnabled()).thenReturn(false);

        // When: Message received
        listener.onMessageReceived(event);

        // Then: No XP is awarded
        verify(chatXpService, never()).awardChatXp(any(), any());
    }

    @Test
    void onMessageReceived_awardsXpForValidMessage() {
        // Given: Valid guild message from human user
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenReturn(ChatXpService.XpAwardResult.awarded(15, 1, 1, false));

        // When: Message received
        listener.onMessageReceived(event);

        // Then: XP is awarded
        verify(chatXpService).awardChatXp("user1", "guild1");
    }

    @Test
    void onMessageReceived_addsLevelUpReactionWhenUserLevelsUp() {
        // Given: User levels up
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenReturn(ChatXpService.XpAwardResult.awarded(15, 1, 2, true));
        when(message.addReaction(any(Emoji.class))).thenReturn(reactionAction);
        when(reactionAction.queue(any(), any())).thenReturn(null);

        // When: Message received
        listener.onMessageReceived(event);

        // Then: Level-up reaction is added
        verify(message).addReaction(any(Emoji.class));
        verify(reactionAction).queue(any(), any());
    }

    @Test
    void onMessageReceived_noReactionWhenNotLeveledUp() {
        // Given: User awarded XP but no level up
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenReturn(ChatXpService.XpAwardResult.awarded(15, 1, 1, false));

        // When: Message received
        listener.onMessageReceived(event);

        // Then: No reaction is added
        verify(message, never()).addReaction(any(Emoji.class));
    }

    @Test
    void onMessageReceived_noReactionWhenNotificationsDisabled() {
        // Given: User levels up but notifications are disabled
        when(chatXpConfig.isLevelUpNotification()).thenReturn(false);
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenReturn(ChatXpService.XpAwardResult.awarded(15, 1, 2, true));

        // When: Message received
        listener.onMessageReceived(event);

        // Then: No reaction is added
        verify(message, never()).addReaction(any(Emoji.class));
    }

    @Test
    void onMessageReceived_handlesXpServiceExceptionGracefully() {
        // Given: XP service throws exception
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenThrow(new RuntimeException("Database error"));

        // When: Message received
        // Then: Should not throw (graceful error handling)
        listener.onMessageReceived(event);

        // XP service was called but exception was caught
        verify(chatXpService).awardChatXp("user1", "guild1");
    }

    @Test
    void onMessageReceived_handlesReactionFailureGracefully() {
        // Given: User levels up but reaction fails
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenReturn(ChatXpService.XpAwardResult.awarded(15, 1, 2, true));
        when(message.addReaction(any(Emoji.class))).thenReturn(reactionAction);

        // Simulate reaction failure via error consumer
        doAnswer(invocation -> {
            Consumer<Throwable> errorConsumer = invocation.getArgument(1);
            errorConsumer.accept(new RuntimeException("Permission denied"));
            return null;
        }).when(reactionAction).queue(any(), any());

        // When: Message received
        // Then: Should not throw (logs warning but continues)
        listener.onMessageReceived(event);

        verify(message).addReaction(any(Emoji.class));
    }

    @Test
    void onMessageReceived_handlesOnCooldownResult() {
        // Given: User is on cooldown
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenReturn(ChatXpService.XpAwardResult.onCooldown());

        // When: Message received
        listener.onMessageReceived(event);

        // Then: No reaction (no level up)
        verify(message, never()).addReaction(any(Emoji.class));
    }

    @Test
    void onMessageReceived_handlesDisabledResult() {
        // Given: Chat XP is disabled (service returns disabled)
        when(chatXpService.awardChatXp("user1", "guild1"))
            .thenReturn(ChatXpService.XpAwardResult.disabled());

        // When: Message received
        listener.onMessageReceived(event);

        // Then: No reaction
        verify(message, never()).addReaction(any(Emoji.class));
    }
}
