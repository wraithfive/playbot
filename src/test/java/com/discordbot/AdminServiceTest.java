package com.discordbot;

import com.discordbot.web.dto.GuildInfo;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AdminService - focusing on permission validation and security
 */
class AdminServiceTest {

    private AdminService adminService;
    private JDA jda;
    private OAuth2AuthorizedClientService authorizedClientService;
    private GuildsCache guildsCache;
    private WebSocketNotificationService webSocketService;

    @BeforeEach
    void setUp() {
        jda = mock(JDA.class);
        authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        guildsCache = mock(GuildsCache.class);
        webSocketService = mock(WebSocketNotificationService.class);

        adminService = new AdminService(jda, authorizedClientService, guildsCache, webSocketService);
    }

    @Test
    @DisplayName("canManageGuild should return false when authentication is null")
    void testCanManageGuild_NullAuth() {
        boolean result = adminService.canManageGuild(null, "123456");
        assertFalse(result, "Should return false for null authentication");
    }

    @Test
    @DisplayName("canManageGuild should return false when user is not admin")
    void testCanManageGuild_NotAdmin() {
        // Mock authentication with non-admin user
        Authentication auth = createMockAuth("user123", 0L); // No permissions

        // Mock OAuth2 client and access token
        mockAccessToken(auth, "mock-token");

        // Mock Discord API response with user's guilds (no admin permission)
        List<Map<String, Object>> userGuilds = new ArrayList<>();
        Map<String, Object> guild = new HashMap<>();
        guild.put("id", "guild123");
        guild.put("name", "Test Guild");
        guild.put("permissions", "0"); // No permissions
        userGuilds.add(guild);

        when(guildsCache.get(eq("mock-token"), any())).thenReturn(userGuilds);

        boolean result = adminService.canManageGuild(auth, "guild123");
        assertFalse(result, "Should return false when user lacks admin permissions");
    }

    @Test
    @DisplayName("canManageGuild should return false when bot is not present in guild")
    void testCanManageGuild_BotNotPresent() {
        // Mock authentication with admin user
        long adminPermissions = Permission.ADMINISTRATOR.getRawValue();
        Authentication auth = createMockAuth("user123", adminPermissions);

        // Mock OAuth2 client
        mockAccessToken(auth, "mock-token");

        // Mock Discord API response with admin permissions
        List<Map<String, Object>> userGuilds = new ArrayList<>();
        Map<String, Object> guild = new HashMap<>();
        guild.put("id", "guild123");
        guild.put("name", "Test Guild");
        guild.put("permissions", String.valueOf(adminPermissions));
        userGuilds.add(guild);

        when(guildsCache.get(eq("mock-token"), any())).thenReturn(userGuilds);

        // Bot is NOT in this guild
        when(jda.getGuildById("guild123")).thenReturn(null);

        boolean result = adminService.canManageGuild(auth, "guild123");
        assertFalse(result, "Should return false when bot is not in guild");
    }

    @Test
    @DisplayName("canManageGuild should return true when user is admin AND bot is present")
    void testCanManageGuild_Success() {
        // Mock authentication with admin user
        long adminPermissions = Permission.ADMINISTRATOR.getRawValue();
        Authentication auth = createMockAuth("user123", adminPermissions);

        // Mock OAuth2 client
        mockAccessToken(auth, "mock-token");

        // Mock Discord API response
        List<Map<String, Object>> userGuilds = new ArrayList<>();
        Map<String, Object> guild = new HashMap<>();
        guild.put("id", "guild123");
        guild.put("name", "Test Guild");
        guild.put("permissions", String.valueOf(adminPermissions));
        userGuilds.add(guild);

        when(guildsCache.get(eq("mock-token"), any())).thenReturn(userGuilds);

        // Bot IS in this guild
        Guild mockGuild = mock(Guild.class);
        when(jda.getGuildById("guild123")).thenReturn(mockGuild);

        boolean result = adminService.canManageGuild(auth, "guild123");
        assertTrue(result, "Should return true when user is admin and bot is present");
    }

    @Test
    @DisplayName("canManageGuild should accept MANAGE_SERVER permission")
    void testCanManageGuild_ManageServerPermission() {
        // Mock authentication with MANAGE_SERVER permission (not full admin)
        long manageServerPermissions = Permission.MANAGE_SERVER.getRawValue();
        Authentication auth = createMockAuth("user123", manageServerPermissions);

        mockAccessToken(auth, "mock-token");

        // Mock Discord API response
        List<Map<String, Object>> userGuilds = new ArrayList<>();
        Map<String, Object> guild = new HashMap<>();
        guild.put("id", "guild123");
        guild.put("name", "Test Guild");
        guild.put("permissions", String.valueOf(manageServerPermissions));
        userGuilds.add(guild);

        when(guildsCache.get(eq("mock-token"), any())).thenReturn(userGuilds);

        Guild mockGuild = mock(Guild.class);
        when(jda.getGuildById("guild123")).thenReturn(mockGuild);

        boolean result = adminService.canManageGuild(auth, "guild123");
        assertTrue(result, "Should return true for MANAGE_SERVER permission");
    }

    @Test
    @DisplayName("generateBotInviteUrl should include required permissions")
    void testGenerateBotInviteUrl() {
        SelfUser selfUser = mock(SelfUser.class);
        when(jda.getSelfUser()).thenReturn(selfUser);
        when(selfUser.getId()).thenReturn("bot123");

        String inviteUrl = adminService.generateBotInviteUrl("guild456");

        assertNotNull(inviteUrl);
        assertTrue(inviteUrl.contains("client_id=bot123"), "Should contain bot client ID");
        assertTrue(inviteUrl.contains("guild_id=guild456"), "Should contain guild ID");
        assertTrue(inviteUrl.contains("scope=bot%20applications.commands"), "Should request bot scope");
        assertTrue(inviteUrl.contains("permissions="), "Should include permissions");
    }

    @Test
    @DisplayName("isUserAdminInGuild should return false for invalid authentication")
    void testIsUserAdminInGuild_InvalidAuth() {
        boolean result = adminService.isUserAdminInGuild(null, "guild123");
        assertFalse(result, "Should return false for null authentication");
    }

    @Test
    @DisplayName("getManageableGuilds should return empty list for null authentication")
    void testGetManageableGuilds_NullAuth() {
        List<GuildInfo> result = adminService.getManageableGuilds(null);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Should return empty list for null authentication");
    }

    @Test
    @DisplayName("getManageableGuilds should only return guilds where user is admin")
    void testGetManageableGuilds_FiltersByPermissions() {
        long adminPermissions = Permission.ADMINISTRATOR.getRawValue();
        Authentication auth = createMockAuth("user123", adminPermissions);

        mockAccessToken(auth, "mock-token");

        // Mock Discord API response with multiple guilds
        List<Map<String, Object>> userGuilds = new ArrayList<>();

        // Guild 1: User is admin, bot is present
        Map<String, Object> guild1 = new HashMap<>();
        guild1.put("id", "guild1");
        guild1.put("name", "Admin Guild");
        guild1.put("icon", "icon1");
        guild1.put("permissions", String.valueOf(adminPermissions));
        userGuilds.add(guild1);

        // Guild 2: User is NOT admin
        Map<String, Object> guild2 = new HashMap<>();
        guild2.put("id", "guild2");
        guild2.put("name", "Non-Admin Guild");
        guild2.put("icon", null);
        guild2.put("permissions", "0");
        userGuilds.add(guild2);

        when(guildsCache.get(eq("mock-token"), any())).thenReturn(userGuilds);

        // Mock JDA guilds
        Guild mockGuild1 = mock(Guild.class);
        when(jda.getGuildById("guild1")).thenReturn(mockGuild1);
        when(jda.getGuildById("guild2")).thenReturn(null);

        List<GuildInfo> result = adminService.getManageableGuilds(auth);

        assertNotNull(result);
        assertEquals(1, result.size(), "Should only return guilds where user is admin");
        assertEquals("guild1", result.get(0).id());
        assertTrue(result.get(0).userIsAdmin());
        assertTrue(result.get(0).botIsPresent());
    }

    // Helper methods

    private Authentication createMockAuth(String userId, long permissions) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", userId);
        attributes.put("username", "testuser");

        OAuth2User oauth2User = new DefaultOAuth2User(
            Collections.emptyList(),
            attributes,
            "id"
        );

        when(auth.getPrincipal()).thenReturn(oauth2User);
        when(auth.getName()).thenReturn(userId);

        return auth;
    }

    private void mockAccessToken(Authentication auth, String tokenValue) {
        OAuth2User oauth2User = (OAuth2User) auth.getPrincipal();
        String userId = oauth2User.getAttribute("id");

        OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
        when(accessToken.getTokenValue()).thenReturn(tokenValue);

        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(accessToken);

        when(authorizedClientService.loadAuthorizedClient(
            eq("discord"),
            eq(userId)
        )).thenReturn(client);
    }
}
