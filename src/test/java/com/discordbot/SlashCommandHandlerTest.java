package com.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.discordbot.repository.UserCooldownRepository;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.QotdSubmissionService;
import com.discordbot.web.service.WebSocketNotificationService;

class SlashCommandHandlerTest {
    @Test
    void testOnGuildJoinRegistersCommands() {
    var cooldownRepo = mock(UserCooldownRepository.class);
    var streamRepo = mock(QotdStreamRepository.class);
    var guildsCache = mock(GuildsCache.class);
    var wsService = mock(WebSocketNotificationService.class);
    var qotdSubmissionService = mock(QotdSubmissionService.class);
    var handler = new SlashCommandHandler(cooldownRepo, streamRepo, guildsCache, wsService, qotdSubmissionService);
        var guild = mock(Guild.class);
        var updateAction = mock(CommandListUpdateAction.class);
        var event = new GuildJoinEvent(null, 0, guild);
        when(guild.updateCommands()).thenReturn(updateAction);
        when(updateAction.addCommands(any(CommandData[].class))).thenReturn(updateAction);
        doNothing().when(updateAction).queue(any(), any());

        handler.onGuildJoin(event);

        verify(guild, times(1)).updateCommands();
        verify(updateAction, times(1)).addCommands(any(CommandData[].class));
        verify(updateAction, times(1)).queue(any(), any());
    }
}
