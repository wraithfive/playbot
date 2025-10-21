package com.discordbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class SlashCommandHandlerTest {
    @Test
    void testOnGuildJoinRegistersCommands() {
        var cooldownRepo = mock(com.discordbot.repository.UserCooldownRepository.class);
        var handler = new SlashCommandHandler(cooldownRepo);
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
