package com.discordbot;

import com.discordbot.web.dto.CreateRoleRequest;
import com.discordbot.web.dto.GachaRoleInfo;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import java.awt.Color;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminServiceRoleColorsGatingTest {

    private JDA jda;
    private OAuth2AuthorizedClientService authorizedClientService;
    private GuildsCache guildsCache;
    private WebSocketNotificationService ws;

    private AdminService service;

    @BeforeEach
    void setup() {
        jda = mock(JDA.class);
        authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        guildsCache = mock(GuildsCache.class);
        ws = mock(WebSocketNotificationService.class);
        service = new AdminService(jda, authorizedClientService, guildsCache, ws);
    }

    @Test
    @DisplayName("createGatchaRole: enhanced colors unsupported -> falls back to solid color with JDA")
    void createRole_enhancedUnsupported_fallsBackToJda() {
        Guild g = mock(Guild.class);
        RoleAction action = mock(RoleAction.class, RETURNS_SELF);
        Role created = mock(Role.class);

        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("Guild");
        when(g.getFeatures()).thenReturn(Collections.emptySet()); // No enhanced color support
        when(g.createRole()).thenReturn(action);
        when(action.complete()).thenReturn(created);
        when(created.getId()).thenReturn("r-solid");
        when(created.getName()).thenReturn("gacha:epic:Grad");
        when(created.getPosition()).thenReturn(3);

        CreateRoleRequest req = new CreateRoleRequest("Grad", "epic", "#123456", "#654321", null);
        GachaRoleInfo dto = service.createGatchaRole("g1", req);
        
        assertEquals("r-solid", dto.id());
        verify(g, times(1)).createRole();
        // Should use setColor() path for solid color since no enhanced support
        verify(action).setColor(any(Color.class));
    }

    @Test
    @DisplayName("createGatchaRole: enhanced colors supported -> uses JDA gradient/holographic API")
    void createRole_enhancedSupported_usesJdaGradient() {
        Guild g = mock(Guild.class);
        RoleAction action = mock(RoleAction.class, RETURNS_SELF);
        Role created = mock(Role.class);

        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("Guild");
        when(g.getFeatures()).thenReturn(Collections.singleton("ENHANCED_ROLE_COLORS")); // Has enhanced support
        when(g.createRole()).thenReturn(action);
        when(action.complete()).thenReturn(created);
        when(created.getId()).thenReturn("rid");
        when(created.getName()).thenReturn("gacha:epic:Grad");
        when(created.getPosition()).thenReturn(1);

        CreateRoleRequest req = new CreateRoleRequest("Grad", "epic", "#123456", "#654321", null);
        GachaRoleInfo dto = service.createGatchaRole("g1", req);
        
        assertEquals("rid", dto.id());
        verify(g).createRole();
        // Should use setGradientColors() for two-color gradient
        verify(action).setGradientColors(any(Color.class), any(Color.class));
        verify(ws, atLeastOnce()).notifyRolesChanged("g1", "created");
    }
}


