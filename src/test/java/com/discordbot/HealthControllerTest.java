package com.discordbot;

import com.discordbot.web.controller.HealthController;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {

    private JDA jda;
    private HealthController controller;

    @BeforeEach
    void setup() {
        jda = mock(JDA.class);
        SelfUser self = mock(SelfUser.class);
        Guild guild = mock(Guild.class);

        when(jda.getStatus()).thenReturn(Status.CONNECTED);
        when(jda.getSelfUser()).thenReturn(self);
        when(self.getName()).thenReturn("TestBot");
        when(jda.getGuilds()).thenReturn(List.of(guild));

        controller = new HealthController(jda);
    }

    @Test
    @DisplayName("/api/health returns UP and bot info")
    void health_ok() {
        ResponseEntity<Map<String, Object>> res = controller.health();
        assertEquals(200, res.getStatusCode().value());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertTrue(((Number) body.get("timestamp")).longValue() > 0);
        Map<?,?> bot = (Map<?,?>) body.get("bot");
        assertNotNull(bot);
        assertEquals(Status.CONNECTED.name(), bot.get("connected"));
        assertEquals("TestBot", bot.get("username"));
        assertEquals(1, bot.get("guilds"));
    }
}
