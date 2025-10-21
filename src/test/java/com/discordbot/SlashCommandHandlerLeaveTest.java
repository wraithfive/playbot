package com.discordbot;

import com.discordbot.repository.UserCooldownRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class SlashCommandHandlerLeaveTest {

    @Test
    void testOnGuildLeaveCleansUpData() {
        UserCooldownRepository repo = mock(UserCooldownRepository.class);
        SlashCommandHandler handler = new SlashCommandHandler(repo);

        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("123");
        when(guild.getName()).thenReturn("Test Guild");

        // Construct a real event with null JDA (constructor: (JDA, responseNumber, Guild))
        GuildLeaveEvent event = new GuildLeaveEvent(null, 0, guild);

        handler.onGuildLeave(event);

        verify(repo, times(1)).deleteByGuildId("123");
    }
}
