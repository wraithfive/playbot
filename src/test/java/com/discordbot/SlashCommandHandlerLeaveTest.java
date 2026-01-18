package com.discordbot;

import com.discordbot.repository.UserCooldownRepository;
import com.discordbot.repository.QotdStreamRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.QotdSubmissionService;
import com.discordbot.web.service.WebSocketNotificationService;

class SlashCommandHandlerLeaveTest {

    @Test
    void testOnGuildLeaveCleansUpData() {
        UserCooldownRepository repo = mock(UserCooldownRepository.class);
        var streamRepo = mock(QotdStreamRepository.class);
        GuildsCache guildsCache = mock(GuildsCache.class);
        WebSocketNotificationService wsService = mock(WebSocketNotificationService.class);
        QotdSubmissionService qotdSubmissionService = mock(QotdSubmissionService.class);
    SlashCommandHandler handler = new SlashCommandHandler(repo, streamRepo, guildsCache, wsService, qotdSubmissionService);

        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("123");
        when(guild.getName()).thenReturn("Test Guild");

        // Construct a real event with null JDA (constructor: (JDA, responseNumber, Guild))
        GuildLeaveEvent event = new GuildLeaveEvent(null, 0, guild);

        handler.onGuildLeave(event);

        verify(repo, times(1)).deleteByGuildId("123");
    }
}
