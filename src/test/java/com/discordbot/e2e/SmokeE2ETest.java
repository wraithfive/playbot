package com.discordbot.e2e;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDA.Status;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E-ish smoke test for the backend API that boots the full Spring context
 * while overriding the JDA bean with a mock. This validates wiring and
 * controller behavior without making real Discord connections.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SmokeE2ETest {
    @MockBean(name = "jda")
    private JDA jda;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockJda() {
        // Provide a JDA mock so the application can start without a real Discord token
        SelfUser self = Mockito.mock(SelfUser.class);
        Guild guild = Mockito.mock(Guild.class);

        Mockito.when(jda.getStatus()).thenReturn(Status.CONNECTED);
        Mockito.when(jda.getSelfUser()).thenReturn(self);
        Mockito.when(self.getName()).thenReturn("E2E-Bot");
        Mockito.when(jda.getGuilds()).thenReturn(List.of(guild));
    }

    @Test
    @DisplayName("Health endpoint returns UP with mocked JDA details")
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.bot.connected").value(Status.CONNECTED.name()))
                .andExpect(jsonPath("$.bot.username").value("E2E-Bot"))
                .andExpect(jsonPath("$.bot.guilds").value(1));
    }

    // JDA is mocked via @MockBean to replace the application's JDA bean during tests
}
