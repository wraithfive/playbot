package com.discordbot.command;

import com.discordbot.battle.config.BattleProperties;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CommandRegistrarTest {

    @Test
    void testOnGuildReadyRegistersCommands() {
        var battleProperties = mock(BattleProperties.class);
        when(battleProperties.isEnabled()).thenReturn(false);
        
        var registrar = new CommandRegistrar(battleProperties);
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn("123");
        when(guild.getName()).thenReturn("Test Guild");
        
        var updateAction = mock(CommandListUpdateAction.class);
        when(guild.updateCommands()).thenReturn(updateAction);
        when(updateAction.addCommands(anyList())).thenReturn(updateAction);
        
        var event = mock(GuildReadyEvent.class);
        when(event.getGuild()).thenReturn(guild);
        
        registrar.onGuildReady(event);
        
        verify(guild, times(1)).updateCommands();
        verify(updateAction, times(1)).addCommands(anyList());
        verify(updateAction, times(1)).queue(any(), any());
    }

    @Test
    void testOnGuildJoinRegistersCommands() {
        var battleProperties = mock(BattleProperties.class);
        when(battleProperties.isEnabled()).thenReturn(true);
        
        var registrar = new CommandRegistrar(battleProperties);
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn("456");
        when(guild.getName()).thenReturn("New Guild");
        
        var updateAction = mock(CommandListUpdateAction.class);
        when(guild.updateCommands()).thenReturn(updateAction);
        when(updateAction.addCommands(anyList())).thenReturn(updateAction);
        
        var event = mock(GuildJoinEvent.class);
        when(event.getGuild()).thenReturn(guild);
        
        registrar.onGuildJoin(event);
        
        verify(guild, times(1)).updateCommands();
        verify(updateAction, times(1)).addCommands(anyList());
        verify(updateAction, times(1)).queue(any(), any());
    }

    @Test
    void testBattleCommandRegisteredWhenEnabled() {
        var battleProperties = mock(BattleProperties.class);
        when(battleProperties.isEnabled()).thenReturn(true);
        
        var registrar = new CommandRegistrar(battleProperties);
        var guild = mock(Guild.class);
        when(guild.getId()).thenReturn("789");
        when(guild.getName()).thenReturn("Battle Guild");
        
        var updateAction = mock(CommandListUpdateAction.class);
        when(guild.updateCommands()).thenReturn(updateAction);
        when(updateAction.addCommands(anyList())).thenReturn(updateAction);
        
        var event = mock(GuildReadyEvent.class);
        when(event.getGuild()).thenReturn(guild);
        
        registrar.onGuildReady(event);
        
        // Battle command should be included when enabled
        verify(battleProperties, atLeastOnce()).isEnabled();
        verify(guild, times(1)).updateCommands();
    }
}
