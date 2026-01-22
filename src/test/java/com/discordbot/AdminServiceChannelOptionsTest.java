package com.discordbot;

import com.discordbot.web.dto.qotd.QotdDtos.ChannelTreeNodeDto;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelType;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AdminService - Channel Options Tree")
class AdminServiceChannelOptionsTest {

    private JDA jda;
    private Guild guild;
    private OAuth2AuthorizedClientService authorizedClientService;
    private GuildsCache guildsCache;
    private WebSocketNotificationService webSocketService;
    
    private TextChannel textChannel1;
    private TextChannel textChannel2;
    private ThreadChannel threadChannel1;
    private ThreadChannel threadChannel2;
    private IThreadContainerUnion parentChannel;
    
    private AdminService adminService;
    
    @BeforeEach
    void setUp() {
        jda = mock(JDA.class);
        guild = mock(Guild.class);
        authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        guildsCache = mock(GuildsCache.class);
        webSocketService = mock(WebSocketNotificationService.class);
        
        textChannel1 = mock(TextChannel.class);
        textChannel2 = mock(TextChannel.class);
        threadChannel1 = mock(ThreadChannel.class);
        threadChannel2 = mock(ThreadChannel.class);
        parentChannel = mock(IThreadContainerUnion.class);
        
        adminService = new AdminService(jda, authorizedClientService, guildsCache, webSocketService);
    }
    
    @Test
    @DisplayName("getChannelOptions returns tree with channels and nested threads")
    void getChannelOptions_returnsTreeStructure() {
        String guildId = "guild123";
        
        // Setup channel 1
        when(textChannel1.getId()).thenReturn("channel1");
        when(textChannel1.getName()).thenReturn("general");
        when(textChannel1.canTalk()).thenReturn(true);
        
        // Setup channel 2
        when(textChannel2.getId()).thenReturn("channel2");
        when(textChannel2.getName()).thenReturn("announcements");
        when(textChannel2.canTalk()).thenReturn(true);
        
        // Setup thread 1 (child of channel1)
        when(threadChannel1.getId()).thenReturn("thread1");
        when(threadChannel1.getName()).thenReturn("discussion-thread");
        when(threadChannel1.getParentChannel()).thenReturn(parentChannel);
        when(threadChannel1.canTalk()).thenReturn(true);
        when(threadChannel1.isArchived()).thenReturn(false);
        when(parentChannel.getId()).thenReturn("channel1");
        
        // Setup thread 2 (child of channel1)
        when(threadChannel2.getId()).thenReturn("thread2");
        when(threadChannel2.getName()).thenReturn("help-thread");
        when(threadChannel2.getParentChannel()).thenReturn(parentChannel);
        when(threadChannel2.canTalk()).thenReturn(true);
        when(threadChannel2.isArchived()).thenReturn(false);
        
        // Setup guild
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1, textChannel2));
        when(guild.getThreadChannels()).thenReturn(Arrays.asList(threadChannel1, threadChannel2));
        
        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);
        
        assertEquals(2, result.size(), "Should return 2 top-level channels");
        
        // Verify channel 1 with threads
        ChannelTreeNodeDto channel1 = result.stream()
            .filter(c -> c.id().equals("channel1"))
            .findFirst()
            .orElseThrow();
        assertEquals("general", channel1.name());
        assertEquals(ChannelType.CHANNEL, channel1.type());
        assertEquals(2, channel1.children().size(), "Channel 1 should have 2 threads");
        
        // Verify threads are children
        ChannelTreeNodeDto thread1 = channel1.children().stream()
            .filter(t -> t.id().equals("thread1"))
            .findFirst()
            .orElseThrow();
        assertEquals("discussion-thread", thread1.name());
        assertEquals(ChannelType.THREAD, thread1.type());
        assertTrue(thread1.children().isEmpty(), "Threads should have no children");
        
        // Verify channel 2 with no threads
        ChannelTreeNodeDto channel2 = result.stream()
            .filter(c -> c.id().equals("channel2"))
            .findFirst()
            .orElseThrow();
        assertEquals("announcements", channel2.name());
        assertEquals(ChannelType.CHANNEL, channel2.type());
        assertTrue(channel2.children().isEmpty(), "Channel 2 should have no threads");
    }
    
    @Test
    @DisplayName("getChannelOptions filters out channels bot cannot talk in")
    void getChannelOptions_filtersNoTalkChannels() {
        String guildId = "guild123";
        
        when(textChannel1.getId()).thenReturn("channel1");
        when(textChannel1.getName()).thenReturn("readable-only");
        when(textChannel1.canTalk()).thenReturn(false); // Bot cannot talk here
        
        when(textChannel2.getId()).thenReturn("channel2");
        when(textChannel2.getName()).thenReturn("writable");
        when(textChannel2.canTalk()).thenReturn(true);
        
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1, textChannel2));
        when(guild.getThreadChannels()).thenReturn(Collections.emptyList());
        
        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);
        
        assertEquals(1, result.size(), "Should only return writable channel");
        assertEquals("channel2", result.get(0).id());
    }
    
    @Test
    @DisplayName("getChannelOptions returns empty list when guild not found")
    void getChannelOptions_guildNotFound_returnsEmptyList() {
        String guildId = "nonexistent";
        when(jda.getGuildById(guildId)).thenReturn(null);
        
        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);
        
        assertTrue(result.isEmpty(), "Should return empty list when guild not found");
    }
    
    @Test
    @DisplayName("getChannelOptions handles guild with no channels")
    void getChannelOptions_noChannels_returnsEmptyList() {
        String guildId = "guild123";
        
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Collections.emptyList());
        when(guild.getThreadChannels()).thenReturn(Collections.emptyList());
        
        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);
        
        assertTrue(result.isEmpty(), "Should return empty list when no channels");
    }
    
    @Test
    @DisplayName("getChannelOptions excludes archived threads")
    void getChannelOptions_excludesArchivedThreads() {
        String guildId = "guild123";
        
        // Setup channel
        when(textChannel1.getId()).thenReturn("channel1");
        when(textChannel1.getName()).thenReturn("general");
        when(textChannel1.canTalk()).thenReturn(true);
        
        // Active thread
        when(threadChannel1.getId()).thenReturn("thread1");
        when(threadChannel1.getName()).thenReturn("active-thread");
        when(threadChannel1.getParentChannel()).thenReturn(parentChannel);
        when(threadChannel1.canTalk()).thenReturn(true);
        when(threadChannel1.isArchived()).thenReturn(false);
        when(parentChannel.getId()).thenReturn("channel1");
        
        // Archived thread
        when(threadChannel2.getId()).thenReturn("thread2");
        when(threadChannel2.getName()).thenReturn("archived-thread");
        when(threadChannel2.getParentChannel()).thenReturn(parentChannel);
        when(threadChannel2.canTalk()).thenReturn(true);
        when(threadChannel2.isArchived()).thenReturn(true);
        
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1));
        when(guild.getThreadChannels()).thenReturn(Arrays.asList(threadChannel1, threadChannel2));
        
        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);
        
        assertEquals(1, result.size(), "Should return 1 channel");
        ChannelTreeNodeDto channel = result.get(0);
        assertEquals(1, channel.children().size(), "Should only include active thread");
        assertEquals("thread1", channel.children().get(0).id());
    }
}
