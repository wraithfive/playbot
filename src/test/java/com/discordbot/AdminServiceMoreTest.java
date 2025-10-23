package com.discordbot;

import com.discordbot.web.dto.BulkRoleCreationResult;
import com.discordbot.web.dto.GachaRoleInfo;
import com.discordbot.web.dto.RoleDeletionResult;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminServiceMoreTest {

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
    @DisplayName("deleteGatchaRole: role not found")
    void deleteGatchaRole_roleNotFound() {
        Guild guild = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getRoleById("r1")).thenReturn(null);

        RoleDeletionResult res = service.deleteGatchaRole("g1", "r1");
        assertFalse(res.success());
        assertEquals("r1", res.roleId());
        assertEquals("Role not found", res.error());
    }

    @Test
    @DisplayName("deleteGatchaRole: non-gacha role rejected")
    void deleteGatchaRole_nonGacha() {
        Guild guild = mock(Guild.class);
        Role role = mock(Role.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getRoleById("r1")).thenReturn(role);
        when(role.getName()).thenReturn("random");

        RoleDeletionResult res = service.deleteGatchaRole("g1", "r1");
        assertFalse(res.success());
        assertEquals("Can only delete gacha roles", res.error());
    }

    @Test
    @DisplayName("deleteGatchaRole: success path")
    void deleteGatchaRole_success() {
        Guild guild = mock(Guild.class);
        Role role = mock(Role.class, RETURNS_DEEP_STUBS);
        when(guild.getName()).thenReturn("G");
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getRoleById("r1")).thenReturn(role);
        when(role.getName()).thenReturn("gacha:rare:Test");
        when(role.getId()).thenReturn("r1");
        when(role.delete().complete()).thenReturn(null);

        RoleDeletionResult res = service.deleteGatchaRole("g1", "r1");
        assertTrue(res.success());
        assertNull(res.error());
        assertEquals("r1", res.roleId());
        assertEquals("gacha:rare:Test", res.roleName());
    }

    @Test
    @DisplayName("deleteGatchaRole: missing permissions error is mapped")
    void deleteGatchaRole_missingPermissions() {
        Guild guild = mock(Guild.class);
        Role role = mock(Role.class, RETURNS_DEEP_STUBS);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getRoleById("r1")).thenReturn(role);
        when(guild.getName()).thenReturn("Guild");
        when(role.getName()).thenReturn("gacha:rare:Test");
        when(role.getId()).thenReturn("r1");
        when(role.delete().complete()).thenThrow(new RuntimeException("Missing Permissions"));

        RoleDeletionResult res = service.deleteGatchaRole("g1", "r1");
        assertFalse(res.success());
        assertNotNull(res.error());
        assertTrue(res.error().contains("Bot lacks permission"));
    }

    @Test
    @DisplayName("deleteBulkGatchaRoles: mixed results and websocket notification on success")
    void deleteBulk_mixed() {
        AdminService spy = spy(service);
        Guild guild = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getName()).thenReturn("G");

        doReturn(new RoleDeletionResult("r1", "gacha:rare:A", true, null))
                .when(spy).deleteGatchaRole("g1", "r1");
        doReturn(new RoleDeletionResult("r2", "gacha:rare:B", false, "err"))
                .when(spy).deleteGatchaRole("g1", "r2");

        var result = spy.deleteBulkGatchaRoles("g1", List.of("r1", "r2"));
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        verify(ws, times(1)).notifyRolesChanged("g1", "deleted");
    }

    @Test
    @DisplayName("createBulkGatchaRoles: creates roles and notifies on success")
    void createBulk_createsAndNotifies() {
        AdminService spy = spy(service);
        Guild guild = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getRolesByName(anyString(), anyBoolean())).thenReturn(Collections.emptyList());

        doReturn(new GachaRoleInfo("id1", "gacha:rare:A", "A", "rare", "#FFFFFF", 1))
                .when(spy).createGatchaRole(eq("g1"), any());

        var reqs = List.of(
                new com.discordbot.web.dto.CreateRoleRequest("A", "rare", "#FFFFFF"),
                new com.discordbot.web.dto.CreateRoleRequest("B", "epic", "#000000")
        );

        BulkRoleCreationResult res = spy.createBulkGatchaRoles("g1", reqs);
        assertEquals(2, res.successCount());
        verify(ws, times(1)).notifyRolesChanged("g1", "created");
    }

    @Test
    @DisplayName("initializeDefaultRoles: evicts cache and delegates to bulk creation")
    void initializeDefaultRoles_delegates() {
        AdminService spy = spy(service);
        doReturn(new BulkRoleCreationResult(11, 0, 0, List.of(), List.of(), List.of()))
                .when(spy).createBulkGatchaRoles(eq("g1"), any());

        BulkRoleCreationResult result = spy.initializeDefaultRoles("g1");
        assertEquals(11, result.successCount());
        verify(guildsCache, atLeastOnce()).evictAll();
    }

    @Test
    @DisplayName("evictGuildsCache uses access token from authorized client")
    void evictGuildsCache_usesToken() {
        Authentication auth = mockAuth("user123");
        mockAccessToken("user123", "token-abc");
        service.evictGuildsCache(auth);
        verify(guildsCache).evictForToken("token-abc");
    }

    // Helpers
    private Authentication mockAuth(String userId) {
        Authentication auth = mock(Authentication.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", userId);
        OAuth2User user = new DefaultOAuth2User(Collections.emptyList(), attributes, "id");
        when(auth.getPrincipal()).thenReturn(user);
        when(auth.getName()).thenReturn(userId);
        return auth;
    }

    private void mockAccessToken(String userId, String token) {
        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessToken.getTokenValue()).thenReturn(token);
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(accessToken);
        when(authorizedClientService.loadAuthorizedClient(eq("discord"), eq(userId)))
                .thenReturn(client);
    }
}
