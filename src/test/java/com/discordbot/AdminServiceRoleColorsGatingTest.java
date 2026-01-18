package com.discordbot;

import com.discordbot.web.dto.CreateRoleRequest;
import com.discordbot.web.dto.GachaRoleInfo;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminServiceRoleColorsGatingTest {

    private JDA jda;
    private OAuth2AuthorizedClientService authorizedClientService;
    private GuildsCache guildsCache;
    private WebSocketNotificationService ws;
    private com.discordbot.discord.DiscordApiClient discordApiClient;

    private AdminService service;

    @BeforeEach
    void setup() {
        jda = mock(JDA.class);
        authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        guildsCache = mock(GuildsCache.class);
        ws = mock(WebSocketNotificationService.class);
        discordApiClient = mock(com.discordbot.discord.DiscordApiClient.class);
        service = new AdminService(jda, authorizedClientService, guildsCache, ws);
    }

    @Test
    @DisplayName("createGatchaRole: enhanced colors unsupported -> uses JDA solid path and does not call REST create")
    void createRole_enhancedUnsupported_fallsBackToJda() {
        Guild g = mock(Guild.class);
        net.dv8tion.jda.api.requests.restaction.RoleAction action = mock(net.dv8tion.jda.api.requests.restaction.RoleAction.class, RETURNS_SELF);
        Role created = mock(Role.class);

        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("Guild");
        when(g.createRole()).thenReturn(action);
        when(action.complete()).thenReturn(created);
        when(created.getId()).thenReturn("r-solid");
        when(created.getName()).thenReturn("gacha:epic:Grad");
        when(created.getColor()).thenReturn(java.awt.Color.decode("#123456"));
        when(created.getPosition()).thenReturn(3);

        when(discordApiClient.guildSupportsEnhancedRoleColors("g1")).thenReturn(false);

        CreateRoleRequest req = new CreateRoleRequest("Grad", "epic", "#123456", "#654321", null);
        GachaRoleInfo dto = service.createGatchaRole("g1", req);
        assertEquals("r-solid", dto.id());
        verify(g, times(1)).createRole();
        verify(discordApiClient, never()).createRoleWithColors(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("createGatchaRole: enhanced colors supported -> calls REST create and does not use JDA createRole")
    void createRole_enhancedSupported_callsApi() {
        Guild g = mock(Guild.class);
        Role created = mock(Role.class);

        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("Guild");
        when(created.getId()).thenReturn("rid");
        when(created.getName()).thenReturn("gacha:epic:Grad");
        when(created.getColor()).thenReturn(null);
        when(created.getPosition()).thenReturn(1);

        // REST path: return role id, and ensure guild can resolve it
        when(discordApiClient.guildSupportsEnhancedRoleColors("g1")).thenReturn(true);
        when(discordApiClient.createRoleWithColors(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn("rid");
        when(g.getRoleById("rid")).thenReturn(created);

        CreateRoleRequest req = new CreateRoleRequest("Grad", "epic", "#123456", "#654321", "#abcdef");
        GachaRoleInfo dto = service.createGatchaRole("g1", req);
        assertEquals("rid", dto.id());
        verify(g, never()).createRole();
        // ws notify should be attempted once
        verify(ws, atLeastOnce()).notifyRolesChanged("g1", "created");
    }
}
