package com.discordbot;

import com.discordbot.web.dto.qotd.QotdDtos.ChannelTreeNodeDto;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QotdDtos - ChannelTreeNodeDto")
class QotdChannelTreeNodeTest {

    @Test
    @DisplayName("ChannelTreeNodeDto - channel with threads")
    void channelTreeNodeDto_channelWithThreads() {
        List<ChannelTreeNodeDto> children = Arrays.asList(
            new ChannelTreeNodeDto("thread1", "Discussion", ChannelType.THREAD),
            new ChannelTreeNodeDto("thread2", "Help", ChannelType.THREAD)
        );
        
        ChannelTreeNodeDto channel = new ChannelTreeNodeDto(
            "channel1",
            "general",
            ChannelType.CHANNEL,
            children
        );
        
        assertEquals("channel1", channel.id());
        assertEquals("general", channel.name());
        assertEquals(ChannelType.CHANNEL, channel.type());
        assertEquals(2, channel.children().size());
        assertEquals("thread1", channel.children().get(0).id());
    }
    
    @Test
    @DisplayName("ChannelTreeNodeDto - thread (leaf node)")
    void channelTreeNodeDto_thread() {
        ChannelTreeNodeDto thread = new ChannelTreeNodeDto(
            "thread1",
            "Discussion",
            ChannelType.THREAD
        );
        
        assertEquals("thread1", thread.id());
        assertEquals("Discussion", thread.name());
        assertEquals(ChannelType.THREAD, thread.type());
        assertTrue(thread.children().isEmpty(), "Threads should have empty children list");
    }
    
    @Test
    @DisplayName("ChannelTreeNodeDto - channel with no threads")
    void channelTreeNodeDto_channelNoThreads() {
        ChannelTreeNodeDto channel = new ChannelTreeNodeDto(
            "channel1",
            "announcements",
            ChannelType.CHANNEL,
            Collections.emptyList()
        );
        
        assertEquals("channel1", channel.id());
        assertEquals("announcements", channel.name());
        assertEquals(ChannelType.CHANNEL, channel.type());
        assertTrue(channel.children().isEmpty());
    }
    
    @Test
    @DisplayName("ChannelType enum has correct values")
    void channelType_enumValues() {
        assertEquals(2, ChannelType.values().length);
        assertEquals(ChannelType.CHANNEL, ChannelType.valueOf("CHANNEL"));
        assertEquals(ChannelType.THREAD, ChannelType.valueOf("THREAD"));
    }
    
    @Test
    @DisplayName("ChannelTreeNodeDto - equals and hashCode")
    void channelTreeNodeDto_equalsHashCode() {
        ChannelTreeNodeDto dto1 = new ChannelTreeNodeDto(
            "id1",
            "name1",
            ChannelType.CHANNEL,
            Collections.emptyList()
        );
        
        ChannelTreeNodeDto dto2 = new ChannelTreeNodeDto(
            "id1",
            "name1",
            ChannelType.CHANNEL,
            Collections.emptyList()
        );
        
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
    
    @Test
    @DisplayName("ChannelTreeNodeDto - toString")
    void channelTreeNodeDto_toString() {
        ChannelTreeNodeDto dto = new ChannelTreeNodeDto(
            "channel1",
            "general",
            ChannelType.CHANNEL,
            Collections.emptyList()
        );
        
        String toString = dto.toString();
        assertTrue(toString.contains("channel1"));
        assertTrue(toString.contains("general"));
        assertTrue(toString.contains("CHANNEL"));
    }
}
