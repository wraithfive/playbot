package com.discordbot;

import com.discordbot.web.dto.qotd.QotdDtos.ChannelTreeNodeDto;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelType;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelStreamStatusDto;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.entity.QotdStream;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.requests.RestAction;
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
    private QotdStreamRepository qotdStreamRepository;
    
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
        qotdStreamRepository = mock(QotdStreamRepository.class);
        
        textChannel1 = mock(TextChannel.class);
        textChannel2 = mock(TextChannel.class);
        threadChannel1 = mock(ThreadChannel.class);
        threadChannel2 = mock(ThreadChannel.class);
        parentChannel = mock(IThreadContainerUnion.class);
        
        adminService = new AdminService(jda, authorizedClientService, guildsCache, webSocketService, qotdStreamRepository);
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
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        // Mock retrieveActiveThreads() API call
        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Arrays.asList(threadChannel1, threadChannel2));
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);

        assertEquals(2, result.size(), "Should return 2 top-level channels");

        // Verify channel 1 with threads
        ChannelTreeNodeDto channel1 = result.stream()
            .filter(c -> c.id().equals("channel1"))
            .findFirst()
            .orElseThrow();
        assertEquals("general", channel1.name());
        assertEquals(ChannelType.CHANNEL, channel1.type());
        assertTrue(channel1.canPost(), "Channel 1 should have canPost=true");
        assertEquals(2, channel1.children().size(), "Channel 1 should have 2 threads");

        // Verify threads are children
        ChannelTreeNodeDto thread1 = channel1.children().stream()
            .filter(t -> t.id().equals("thread1"))
            .findFirst()
            .orElseThrow();
        assertEquals("discussion-thread", thread1.name());
        assertEquals(ChannelType.THREAD, thread1.type());
        assertTrue(thread1.canPost(), "Thread 1 should have canPost=true");
        assertTrue(thread1.children().isEmpty(), "Threads should have no children");

        // Verify channel 2 with no threads
        ChannelTreeNodeDto channel2 = result.stream()
            .filter(c -> c.id().equals("channel2"))
            .findFirst()
            .orElseThrow();
        assertEquals("announcements", channel2.name());
        assertEquals(ChannelType.CHANNEL, channel2.type());
        assertTrue(channel2.canPost(), "Channel 2 should have canPost=true");
        assertTrue(channel2.children().isEmpty(), "Channel 2 should have no threads");
    }
    
    @Test
    @DisplayName("getChannelOptions includes all channels with canPost status")
    void getChannelOptions_includesAllChannelsWithCanPostStatus() {
        String guildId = "guild123";

        when(textChannel1.getId()).thenReturn("channel1");
        when(textChannel1.getName()).thenReturn("readable-only");
        when(textChannel1.getPosition()).thenReturn(0);
        when(textChannel1.canTalk()).thenReturn(false); // Bot cannot talk here

        when(textChannel2.getId()).thenReturn("channel2");
        when(textChannel2.getName()).thenReturn("writable");
        when(textChannel2.getPosition()).thenReturn(1);
        when(textChannel2.canTalk()).thenReturn(true);

        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1, textChannel2));
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Collections.emptyList());
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);

        assertEquals(2, result.size(), "Should return all channels");

        // Find channels by id
        ChannelTreeNodeDto ch1 = result.stream().filter(c -> c.id().equals("channel1")).findFirst().orElseThrow();
        ChannelTreeNodeDto ch2 = result.stream().filter(c -> c.id().equals("channel2")).findFirst().orElseThrow();

        assertFalse(ch1.canPost(), "Channel 1 should have canPost=false");
        assertTrue(ch2.canPost(), "Channel 2 should have canPost=true");
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
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Collections.emptyList());
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

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
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        // Note: retrieveActiveThreads only returns non-archived threads, so we only include threadChannel1
        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Arrays.asList(threadChannel1));
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);

        assertEquals(1, result.size(), "Should return 1 channel");
        ChannelTreeNodeDto channel = result.get(0);
        assertEquals(1, channel.children().size(), "Should only include active thread");
        assertEquals("thread1", channel.children().get(0).id());
    }

    @Test
    @DisplayName("getChannelOptions includes threads with canPost status")
    void getChannelOptions_includesThreadsWithCanPostStatus() {
        String guildId = "guild123";

        // Setup channel
        when(textChannel1.getId()).thenReturn("channel1");
        when(textChannel1.getName()).thenReturn("general");
        when(textChannel1.getPosition()).thenReturn(0);
        when(textChannel1.canTalk()).thenReturn(true);

        // Thread where bot CAN talk
        when(threadChannel1.getId()).thenReturn("thread1");
        when(threadChannel1.getName()).thenReturn("readable-thread");
        when(threadChannel1.getParentChannel()).thenReturn(parentChannel);
        when(threadChannel1.canTalk()).thenReturn(true);
        when(threadChannel1.isArchived()).thenReturn(false);
        when(parentChannel.getId()).thenReturn("channel1");

        // Thread where bot CANNOT talk
        when(threadChannel2.getId()).thenReturn("thread2");
        when(threadChannel2.getName()).thenReturn("readonly-thread");
        when(threadChannel2.getParentChannel()).thenReturn(parentChannel);
        when(threadChannel2.canTalk()).thenReturn(false); // Bot cannot talk here
        when(threadChannel2.isArchived()).thenReturn(false);

        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1));
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Arrays.asList(threadChannel1, threadChannel2));
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

        List<ChannelTreeNodeDto> result = adminService.getChannelOptions(guildId);

        assertEquals(1, result.size(), "Should return 1 channel");
        ChannelTreeNodeDto channel = result.get(0);
        assertEquals(2, channel.children().size(), "Should include both threads");

        // Verify canPost status on threads
        ChannelTreeNodeDto th1 = channel.children().stream().filter(t -> t.id().equals("thread1")).findFirst().orElseThrow();
        ChannelTreeNodeDto th2 = channel.children().stream().filter(t -> t.id().equals("thread2")).findFirst().orElseThrow();
        assertTrue(th1.canPost(), "Thread 1 should have canPost=true");
        assertFalse(th2.canPost(), "Thread 2 should have canPost=false");
    }

    @Test
    @DisplayName("getStreamStatusForAllChannels returns status for all channels")
    void getStreamStatusForAllChannels_returnsStatusForChannels() {
        String guildId = "guild123";

        // Setup channels
        when(textChannel1.getId()).thenReturn("channel1");
        when(textChannel2.getId()).thenReturn("channel2");

        // Setup threads
        when(threadChannel1.getId()).thenReturn("thread1");
        when(threadChannel2.getId()).thenReturn("thread2");

        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1, textChannel2));
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Arrays.asList(threadChannel1, threadChannel2));
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

        // Mock streams: channel1 has enabled, channel2 has only configured, threads have none
        QotdStream stream1 = mock(QotdStream.class);
        when(stream1.getChannelId()).thenReturn("channel1");
        when(stream1.getEnabled()).thenReturn(true);
        
        QotdStream stream2 = mock(QotdStream.class);
        when(stream2.getChannelId()).thenReturn("channel2");
        when(stream2.getEnabled()).thenReturn(false);
        
        when(qotdStreamRepository.findByGuildIdOrderByChannelIdAscIdAsc(guildId))
            .thenReturn(Arrays.asList(stream1, stream2));
        
        List<ChannelStreamStatusDto> result = adminService.getStreamStatusForAllChannels(guildId);
        
        // Should return 4 entries (2 channels + 2 threads)
        assertEquals(4, result.size());
        
        // Verify channel1: has configured and enabled
        ChannelStreamStatusDto ch1 = result.stream()
            .filter(s -> s.channelId().equals("channel1"))
            .findFirst()
            .orElseThrow();
        assertTrue(ch1.hasConfigured());
        assertTrue(ch1.hasEnabled());
        
        // Verify channel2: has configured but not enabled
        ChannelStreamStatusDto ch2 = result.stream()
            .filter(s -> s.channelId().equals("channel2"))
            .findFirst()
            .orElseThrow();
        assertTrue(ch2.hasConfigured());
        assertFalse(ch2.hasEnabled());
        
        // Verify threads: not configured
        ChannelStreamStatusDto th1 = result.stream()
            .filter(s -> s.channelId().equals("thread1"))
            .findFirst()
            .orElseThrow();
        assertFalse(th1.hasConfigured());
        assertFalse(th1.hasEnabled());
    }
    
    @Test
    @DisplayName("getStreamStatusForAllChannels includes all channels regardless of permissions")
    void getStreamStatusForAllChannels_includesAllChannels() {
        String guildId = "guild123";

        when(textChannel1.getId()).thenReturn("channel1");
        when(textChannel2.getId()).thenReturn("channel2");

        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1, textChannel2));
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Collections.emptyList());
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

        when(qotdStreamRepository.findByGuildIdOrderByChannelIdAscIdAsc(guildId))
            .thenReturn(Collections.emptyList());

        List<ChannelStreamStatusDto> result = adminService.getStreamStatusForAllChannels(guildId);

        assertEquals(2, result.size(), "Should return all channels");
        assertTrue(result.stream().anyMatch(s -> s.channelId().equals("channel1")));
        assertTrue(result.stream().anyMatch(s -> s.channelId().equals("channel2")));
    }

    @Test
    @DisplayName("getStreamStatusForAllChannels excludes archived threads")
    void getStreamStatusForAllChannels_excludesArchivedThreads() {
        String guildId = "guild123";

        when(textChannel1.getId()).thenReturn("channel1");

        // Active thread
        when(threadChannel1.getId()).thenReturn("thread1");

        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannels()).thenReturn(Arrays.asList(textChannel1));
        when(guild.getNewsChannels()).thenReturn(Collections.emptyList());
        when(guild.getForumChannels()).thenReturn(Collections.emptyList());

        // retrieveActiveThreads only returns non-archived threads from Discord API
        @SuppressWarnings("unchecked")
        RestAction<List<ThreadChannel>> threadAction = mock(RestAction.class);
        when(threadAction.complete()).thenReturn(Arrays.asList(threadChannel1)); // Only active thread
        when(guild.retrieveActiveThreads()).thenReturn(threadAction);

        when(qotdStreamRepository.findByGuildIdOrderByChannelIdAscIdAsc(guildId))
            .thenReturn(Collections.emptyList());

        List<ChannelStreamStatusDto> result = adminService.getStreamStatusForAllChannels(guildId);

        assertEquals(2, result.size()); // 1 channel + 1 active thread
        assertTrue(result.stream().anyMatch(s -> s.channelId().equals("channel1")));
        assertTrue(result.stream().anyMatch(s -> s.channelId().equals("thread1")));
    }
}