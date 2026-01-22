package com.discordbot;

import com.discordbot.web.controller.ServerController;
import com.discordbot.web.dto.GuildInfo;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelTreeNodeDto;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelType;
import com.discordbot.web.dto.qotd.QotdDtos.ChannelStreamStatusDto;
import com.discordbot.web.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerControllerTest {

    private AdminService adminService;
    private ServerController controller;
    private Authentication auth;

    @BeforeEach
    void setup() {
        adminService = mock(AdminService.class);
        controller = new ServerController(adminService);
        auth = createMockAuth("user123");
    }

    @Test
    @DisplayName("getChannelOptions enforces auth and manage permissions")
    void getChannelOptions_authz() {
        // Unauthenticated
        ResponseEntity<List<ChannelTreeNodeDto>> unauth = controller.getChannelOptions("g1", null);
        assertEquals(401, unauth.getStatusCode().value());

        // Authenticated but cannot manage
        when(adminService.canManageGuild(auth, "g1")).thenReturn(false);
        ResponseEntity<List<ChannelTreeNodeDto>> forbidden = controller.getChannelOptions("g1", auth);
        assertEquals(403, forbidden.getStatusCode().value());
    }

    @Test
    @DisplayName("getChannelOptions returns tree nodes when authorized")
    void getChannelOptions_ok() {
        when(adminService.canManageGuild(auth, "g1")).thenReturn(true);

        List<ChannelTreeNodeDto> nodes = Arrays.asList(
            new ChannelTreeNodeDto("ch1", "general", ChannelType.CHANNEL, true, Collections.emptyList())
        );
        when(adminService.getChannelOptions("g1")).thenReturn(nodes);

        ResponseEntity<List<ChannelTreeNodeDto>> res = controller.getChannelOptions("g1", auth);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(1, Objects.requireNonNull(res.getBody()).size());
        assertEquals("ch1", res.getBody().get(0).id());
    }

    @Test
    @DisplayName("getStreamStatus enforces auth and manage permissions")
    void getStreamStatus_authz() {
        // Unauthenticated
        ResponseEntity<List<ChannelStreamStatusDto>> unauth = controller.getStreamStatus("g1", null);
        assertEquals(401, unauth.getStatusCode().value());

        // Authenticated but cannot manage
        when(adminService.canManageGuild(auth, "g1")).thenReturn(false);
        ResponseEntity<List<ChannelStreamStatusDto>> forbidden = controller.getStreamStatus("g1", auth);
        assertEquals(403, forbidden.getStatusCode().value());
    }

    @Test
    @DisplayName("getStreamStatus returns status list when authorized")
    void getStreamStatus_ok() {
        when(adminService.canManageGuild(auth, "g1")).thenReturn(true);

        List<ChannelStreamStatusDto> statusList = Arrays.asList(
            new ChannelStreamStatusDto("ch1", true, true),   // has configured and enabled
            new ChannelStreamStatusDto("ch2", true, false),  // has configured but not enabled
            new ChannelStreamStatusDto("ch3", false, false)  // no configuration
        );
        when(adminService.getStreamStatusForAllChannels("g1")).thenReturn(statusList);

        ResponseEntity<List<ChannelStreamStatusDto>> res = controller.getStreamStatus("g1", auth);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(3, Objects.requireNonNull(res.getBody()).size());
        
        // Verify the batch data is correct
        ChannelStreamStatusDto first = res.getBody().get(0);
        assertEquals("ch1", first.channelId());
        assertTrue(first.hasConfigured());
        assertTrue(first.hasEnabled());
        
        ChannelStreamStatusDto second = res.getBody().get(1);
        assertEquals("ch2", second.channelId());
        assertTrue(second.hasConfigured());
        assertFalse(second.hasEnabled());
    }

    @Test
    @DisplayName("getManageableServers returns 401 when unauthenticated")
    void getServers_unauthenticated() {
        ResponseEntity<List<GuildInfo>> res = controller.getManageableServers(null);
        assertEquals(401, res.getStatusCode().value());
    }

    @Test
    @DisplayName("getManageableServers returns list when authenticated")
    void getServers_ok() {
    List<GuildInfo> guilds = Arrays.asList(new GuildInfo("g1", "Guild One", null, true, true, false));
        when(adminService.getManageableGuilds(auth)).thenReturn(guilds);

        ResponseEntity<List<GuildInfo>> res = controller.getManageableServers(auth);
        assertEquals(200, res.getStatusCode().value());
        assertEquals(1, Objects.requireNonNull(res.getBody()).size());
    }

    @Test
    @DisplayName("getServerInfo returns 403 if cannot manage guild")
    void getServerInfo_forbidden() {
        when(adminService.canManageGuild(auth, "g1")).thenReturn(false);
        ResponseEntity<GuildInfo> res = controller.getServerInfo("g1", auth);
        assertEquals(403, res.getStatusCode().value());
    }

    @Test
    @DisplayName("getServerInfo returns 200 with guild match or 404 when absent")
    void getServerInfo_okOrNotFound() {
        when(adminService.canManageGuild(auth, "g1")).thenReturn(true);
    List<GuildInfo> guilds = Arrays.asList(new GuildInfo("g1", "Guild One", null, true, true, false));
        when(adminService.getManageableGuilds(auth)).thenReturn(guilds);
        assertEquals(200, controller.getServerInfo("g1", auth).getStatusCode().value());

        when(adminService.getManageableGuilds(auth)).thenReturn(Collections.emptyList());
        assertEquals(404, controller.getServerInfo("g1", auth).getStatusCode().value());
    }

    @Test
    @DisplayName("getBotInviteUrl enforces auth and admin perms")
    void getInvite_authz() {
        assertEquals(401, controller.getBotInviteUrl("g1", null).getStatusCode().value());
        when(adminService.isUserActualAdminInGuild(auth, "g1")).thenReturn(false);
        assertEquals(403, controller.getBotInviteUrl("g1", auth).getStatusCode().value());
    }

    @Test
    @DisplayName("getBotInviteUrl returns invite when authorized")
    void getInvite_ok() {
        when(adminService.isUserActualAdminInGuild(auth, "g1")).thenReturn(true);
        when(adminService.generateBotInviteUrl("g1")).thenReturn("https://invite");
        ResponseEntity<Map<String,String>> res = controller.getBotInviteUrl("g1", auth);
        assertEquals(200, res.getStatusCode().value());
        assertEquals("https://invite", Objects.requireNonNull(res.getBody()).get("inviteUrl"));
    }

    @Test
    @DisplayName("removeBot enforces auth and manage permissions and handles errors")
    void removeBot_paths() {
        assertEquals(401, controller.removeBot("g1", null).getStatusCode().value());

        when(adminService.canManageGuild(auth, "g1")).thenReturn(false);
        assertEquals(403, controller.removeBot("g1", auth).getStatusCode().value());

        when(adminService.canManageGuild(auth, "g1")).thenReturn(true);
        doThrow(new IllegalArgumentException("bad")).when(adminService).leaveBotFromGuild("g1");
        assertEquals(400, controller.removeBot("g1", auth).getStatusCode().value());

        doThrow(new RuntimeException("boom")).when(adminService).leaveBotFromGuild("g1");
        assertEquals(500, controller.removeBot("g1", auth).getStatusCode().value());

        // Success path
        doNothing().when(adminService).leaveBotFromGuild("g1");
        ResponseEntity<Map<String,String>> ok = controller.removeBot("g1", auth);
        assertEquals(200, ok.getStatusCode().value());
    }

    @Test
    @DisplayName("refreshGuildsCache returns 401 without auth and 200 with auth")
    void refresh_cache() {
        assertEquals(401, controller.refreshGuildsCache(null).getStatusCode().value());
        assertEquals(200, controller.refreshGuildsCache(auth).getStatusCode().value());
        verify(adminService, times(1)).evictGuildsCache(auth);
    }

    private Authentication createMockAuth(String userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", userId);
        attributes.put("username", "testuser");

        OAuth2User oauth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "id");
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(authentication.getName()).thenReturn(userId);

        return authentication;
    }
}
