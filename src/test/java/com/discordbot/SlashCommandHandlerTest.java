package com.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.QotdSubmissionService;
import com.discordbot.web.service.WebSocketNotificationService;

class SlashCommandHandlerTest {
    @Test
    void testOnGuildJoinEvictsCache() {
        var cooldownRepo = mock(com.discordbot.repository.UserCooldownRepository.class);
        var streamRepo = mock(com.discordbot.repository.QotdStreamRepository.class);
        var guildsCache = mock(GuildsCache.class);
        var wsService = mock(WebSocketNotificationService.class);
        var qotdSubmissionService = mock(QotdSubmissionService.class);
        var apiClient = new com.discordbot.discord.DiscordApiClient();
        var characterAutocompleteHandler = mock(com.discordbot.battle.controller.CharacterAutocompleteHandler.class);
        var handler = new SlashCommandHandler(cooldownRepo, streamRepo, guildsCache, wsService, qotdSubmissionService, apiClient, characterAutocompleteHandler);
        
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn("123");
        when(guild.getName()).thenReturn("Test Guild");
        var event = new GuildJoinEvent(null, 0, guild);

        handler.onGuildJoin(event);

        // Verify cache was evicted
        verify(guildsCache, times(1)).evictAll();
        
        // Verify WebSocket notification was sent
        verify(wsService, times(1)).notifyGuildJoined("123", "Test Guild");
    }
}
