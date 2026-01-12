package com.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Additional branch coverage for ColorGachaHandler. */
@SuppressWarnings({"removal"})
class ColorGachaHandlerMoreTest {

    private ColorGachaHandler handler;

    @BeforeEach
    void setup() {
        handler = new ColorGachaHandler();
    }

    @Test
    @DisplayName("colors in DMs should be server-only")
    void testColors_NotFromGuild() {
        MessageReceivedEvent event = createMockEvent("!colors", false);
        handler.onMessageReceived(event);
        verify(event.getChannel()).sendMessage(contains("only be used in a server"));
    }

    @Test
    @DisplayName("mycolor in DMs should be server-only")
    void testMyColor_NotFromGuild() {
        MessageReceivedEvent event = createMockEvent("!mycolor", false);
        handler.onMessageReceived(event);
        verify(event.getChannel()).sendMessage(contains("only be used in a server"));
    }

    @Test
    @DisplayName("roll returns early if member is null")
    void testRoll_MemberNull() {
        MessageReceivedEvent event = createMockEvent("!roll", true);
        when(event.getMember()).thenReturn(null);
        handler.onMessageReceived(event);
        // No further interactions like addRoleToMember should happen
        verify(event.getGuild(), never()).addRoleToMember(any(Member.class), any(Role.class));
    }

    @Test
    @DisplayName("testroll returns early if member is null")
    void testTestRoll_MemberNull() {
        MessageReceivedEvent event = createMockEvent("!testroll", true);
        when(event.getMember()).thenReturn(null);
        handler.onMessageReceived(event);
        verify(event.getGuild(), never()).addRoleToMember(any(Member.class), any(Role.class));
    }

    @Test
    @DisplayName("mycolor returns early if member is null")
    void testMyColor_MemberNull() {
        MessageReceivedEvent event = createMockEvent("!mycolor", true);
        when(event.getMember()).thenReturn(null);
        handler.onMessageReceived(event);
        // Should not try to send an embed or message
        verify(event.getChannel(), never()).sendMessageEmbeds(any());
    }

    @Test
    @DisplayName("onReady executes without error (logs)")
    void testOnReady_Logs() {
        ReadyEvent ready = mock(ReadyEvent.class);
        JDA jda = mock(JDA.class);
        SelfUser self = mock(SelfUser.class);
        when(ready.getJDA()).thenReturn(jda);
        when(jda.getSelfUser()).thenReturn(self);
        when(self.getName()).thenReturn("Playbot");
        handler.onReady(ready);
        // No assertions; this covers the logging path
    }

    private MessageReceivedEvent createMockEvent(String content, boolean fromGuild) {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        User author = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        Message message = mock(Message.class);
        MessageCreateAction action = mock(MessageCreateAction.class);

        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(false);
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn(content);
        when(event.isFromGuild()).thenReturn(fromGuild);
        when(event.getChannel()).thenReturn(channel);

        // Provide a default guild to avoid NPE when verifying no interactions
        Guild guild = mock(Guild.class);
        when(event.getGuild()).thenReturn(guild);

        when(channel.sendMessage(anyString())).thenReturn(action);
        when(channel.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(action);
        doNothing().when(action).queue();

        return event;
    }
}
