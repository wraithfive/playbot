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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

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
        com.discordbot.discord.DiscordApiClient discordApiClient = mock(com.discordbot.discord.DiscordApiClient.class);
        service = new AdminService(jda, authorizedClientService, guildsCache, ws, discordApiClient);
    }

    @Test
    @DisplayName("getManageableGuilds: includes only admin guilds and flags bot presence")
    void getManageableGuilds_includesAdminAndBotFlag() {
        Authentication auth = mockAuth("user1");
        mockAccessToken("user1", "tok-1");

        Map<String, Object> adminPresent = new HashMap<>();
        adminPresent.put("id", "g1");
        adminPresent.put("name", "Guild One");
        adminPresent.put("icon", "abc123");
        adminPresent.put("permissions", String.valueOf(net.dv8tion.jda.api.Permission.ADMINISTRATOR.getRawValue()));

        Map<String, Object> adminAbsent = new HashMap<>();
        adminAbsent.put("id", "g2");
        adminAbsent.put("name", "Guild Two");
        adminAbsent.put("icon", null);
        adminAbsent.put("permissions", String.valueOf(net.dv8tion.jda.api.Permission.MANAGE_SERVER.getRawValue()));

        Map<String, Object> nonAdmin = new HashMap<>();
        nonAdmin.put("id", "g3");
        nonAdmin.put("name", "Guild Three");
        nonAdmin.put("permissions", "0");

        when(guildsCache.get(eq("tok-1"), any())).thenReturn(List.of(adminPresent, adminAbsent, nonAdmin));

        Guild g1 = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(g1);
        when(jda.getGuildById("g2")).thenReturn(null);
        when(jda.getGuilds()).thenReturn(List.of(g1));
        when(g1.getId()).thenReturn("g1");

        var list = service.getManageableGuilds(auth);
        assertEquals(2, list.size(), "Only admin guilds should be included");
        var first = list.get(0);
        var second = list.get(1);
        // Order follows input iteration; verify flags and iconUrl logic
        assertEquals("g1", first.id());
        assertTrue(first.botIsPresent());
        assertTrue(first.iconUrl().contains("/icons/g1/abc123.png"));

        assertEquals("g2", second.id());
        assertFalse(second.botIsPresent());
        assertNull(second.iconUrl());
    }

    @Test
    @DisplayName("deleteRolesByPrefix: deletion errors are handled and not counted")
    void deleteRolesByPrefix_deleteErrorHandled() {
        Guild g = mock(Guild.class);
        Role r1 = mock(Role.class, RETURNS_DEEP_STUBS);
        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("G");
        when(g.getRoles()).thenReturn(List.of(r1));
        when(r1.getName()).thenReturn("gacha:old:Red");
        when(r1.delete().complete()).thenThrow(new RuntimeException("boom"));

        int deleted = service.deleteRolesByPrefix("g1", "gacha:");
        assertEquals(0, deleted);
    }

    @Test
    @DisplayName("createGatchaRole: websocket notify failures are ignored")
    void createGatchaRole_wsNotifyFailureIgnored() {
        Guild g = mock(Guild.class);
        net.dv8tion.jda.api.requests.restaction.RoleAction action = mock(net.dv8tion.jda.api.requests.restaction.RoleAction.class, RETURNS_SELF);
        Role created = mock(Role.class);
        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("G");
        when(g.createRole()).thenReturn(action);
        when(action.complete()).thenReturn(created);
        when(created.getId()).thenReturn("rid");
        when(created.getName()).thenReturn("gacha:epic:Blue");
        when(created.getColor()).thenReturn(java.awt.Color.BLUE);
        when(created.getPosition()).thenReturn(2);

        doThrow(new RuntimeException("ws boom")).when(ws).notifyRolesChanged("g1", "created");

        var dto = service.createGatchaRole("g1", new com.discordbot.web.dto.CreateRoleRequest("Blue","epic","#0000FF", null, null));
        assertEquals("rid", dto.id());
        assertEquals("Blue", dto.displayName());
    }

    @Test
    @DisplayName("generateBotInviteUrl contains client, perms and guildId")
    void generateBotInviteUrl_format() {
    var selfUser = mock(net.dv8tion.jda.api.entities.SelfUser.class);
        when(selfUser.getId()).thenReturn("client-123");
        when(jda.getSelfUser()).thenReturn(selfUser);

        String url = service.generateBotInviteUrl("g999");
        assertTrue(url.contains("client_id=client-123"));
        assertTrue(url.contains("guild_id=g999"));
        assertTrue(url.contains("scope=bot%20applications.commands"));
        assertTrue(url.startsWith("https://discord.com/api/oauth2/authorize?"));
    }

    @Test
    @DisplayName("generateBotInviteUrl includes expected permissions bitmask")
    void generateBotInviteUrl_permissionsBitmask() {
        var selfUser = mock(net.dv8tion.jda.api.entities.SelfUser.class);
        when(selfUser.getId()).thenReturn("cid");
        when(jda.getSelfUser()).thenReturn(selfUser);

        long expected = net.dv8tion.jda.api.Permission.MANAGE_ROLES.getRawValue()
            | net.dv8tion.jda.api.Permission.VIEW_CHANNEL.getRawValue()
            | net.dv8tion.jda.api.Permission.MESSAGE_SEND.getRawValue()
            | net.dv8tion.jda.api.Permission.MESSAGE_EMBED_LINKS.getRawValue()
            | net.dv8tion.jda.api.Permission.MESSAGE_HISTORY.getRawValue()
            | net.dv8tion.jda.api.Permission.MESSAGE_ATTACH_FILES.getRawValue();

        String url = service.generateBotInviteUrl("g1");
        assertTrue(url.contains("permissions=" + expected), "URL should contain expected permissions mask");
    }

    @Test
    @DisplayName("canManageGuild: true only when user is admin and bot present")
    void canManageGuild_matrix() {
        AdminService spySvc = spy(service);

        // Case 1: admin true, bot present -> true
        doReturn(true).when(spySvc).isUserAdminInGuild(any(), eq("g1"));
        Guild g1 = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(g1);
        assertTrue(spySvc.canManageGuild(mock(org.springframework.security.core.Authentication.class), "g1"));

        // Case 2: admin true, bot missing -> false
        doReturn(true).when(spySvc).isUserAdminInGuild(any(), eq("g2"));
        when(jda.getGuildById("g2")).thenReturn(null);
        assertFalse(spySvc.canManageGuild(mock(org.springframework.security.core.Authentication.class), "g2"));

        // Case 3: admin false, bot present -> false
        doReturn(false).when(spySvc).isUserAdminInGuild(any(), eq("g3"));
        Guild g3 = mock(Guild.class);
        when(jda.getGuildById("g3")).thenReturn(g3);
        assertFalse(spySvc.canManageGuild(mock(org.springframework.security.core.Authentication.class), "g3"));
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
    @DisplayName("deleteBulkGatchaRoles: zero successes should not notify websocket")
    void deleteBulk_zeroSuccess_noNotify() {
        AdminService spy = spy(service);
        Guild guild = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getName()).thenReturn("G");

        doReturn(new RoleDeletionResult("r1", "gacha:rare:A", false, "err1"))
            .when(spy).deleteGatchaRole("g1", "r1");
        doReturn(new RoleDeletionResult("r2", "gacha:rare:B", false, "err2"))
            .when(spy).deleteGatchaRole("g1", "r2");

        var result = spy.deleteBulkGatchaRoles("g1", List.of("r1", "r2"));
        assertEquals(0, result.successCount());
        assertEquals(2, result.failureCount());
        verify(ws, never()).notifyRolesChanged(anyString(), eq("deleted"));
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
                    new com.discordbot.web.dto.CreateRoleRequest("A", "rare", "#FFFFFF", null, null),
                    new com.discordbot.web.dto.CreateRoleRequest("B", "epic", "#000000", null, null)
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
    @DisplayName("createBulkGatchaRoles: skips duplicates and collects errors; no notify when none created")
    void createBulk_skipsAndErrors() {
        AdminService spy = spy(service);
        Guild guild = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(guild);

        // First request is a duplicate (role already exists)
        Role existing = mock(Role.class);
        when(guild.getRolesByName("gacha:rare:A", true)).thenReturn(List.of(existing));

        // Second request will attempt creation but fail
        when(guild.getRolesByName("gacha:epic:B", true)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("boom")).when(spy).createGatchaRole(eq("g1"), any());

        var reqs = List.of(
              new com.discordbot.web.dto.CreateRoleRequest("A", "rare", "#FFFFFF", null, null),
              new com.discordbot.web.dto.CreateRoleRequest("B", "epic", "#000000", null, null)
        );

        var result = spy.createBulkGatchaRoles("g1", reqs);
        assertEquals(0, result.successCount());
        assertEquals(1, result.skippedCount());
        assertEquals(1, result.failureCount());
        // No websocket notify when nothing created
        verify(ws, never()).notifyRolesChanged(anyString(), eq("created"));
    }

    @Test
    @DisplayName("getGatchaRoles: returns only prefixed roles and maps fields")
    void getGatchaRoles_filtersAndMaps() {
        Guild g = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(g);

        Role g1 = mock(Role.class);
        when(g1.getName()).thenReturn("gacha:rare:Blue");
        when(g1.getId()).thenReturn("r1");
        when(g1.getColor()).thenReturn(java.awt.Color.BLUE);
        when(g1.getPosition()).thenReturn(5);

        Role normal = mock(Role.class);
        when(normal.getName()).thenReturn("Member");

        when(g.getRoles()).thenReturn(List.of(g1, normal));

        var roles = service.getGatchaRoles("g1");
        assertEquals(1, roles.size());
        var dto = roles.get(0);
        assertEquals("r1", dto.id());
        assertEquals("gacha:rare:Blue", dto.fullName());
        assertEquals("Blue", dto.displayName());
        assertEquals("rare", dto.rarity());
        assertTrue(dto.colorHex().startsWith("#"));
        assertEquals(5, dto.position());
    }

    @Test
    @DisplayName("getGatchaRoles: maps null color to null hex")
    void getGatchaRoles_nullColorMapsToNull() {
        Guild g = mock(Guild.class);
        when(jda.getGuildById("g2")).thenReturn(g);

        Role r = mock(Role.class);
        when(r.getName()).thenReturn("gacha:epic:NoColor");
        when(r.getId()).thenReturn("rX");
        when(r.getColor()).thenReturn(null);
        when(r.getPosition()).thenReturn(7);

        when(g.getRoles()).thenReturn(List.of(r));

        var roles = service.getGatchaRoles("g2");
        assertEquals(1, roles.size());
        assertNull(roles.get(0).colorHex());
        assertEquals("NoColor", roles.get(0).displayName());
        assertEquals("epic", roles.get(0).rarity());
    }

    @Test
    @DisplayName("getGatchaRoles: parses no-rarity format gacha:ColorOnly")
    void getGatchaRoles_noRarity_parsing() {
        Guild g = mock(Guild.class);
        when(jda.getGuildById("g3")).thenReturn(g);

        Role r = mock(Role.class);
        when(r.getName()).thenReturn("gacha:Gold");
        when(r.getId()).thenReturn("rN");
        when(r.getColor()).thenReturn(java.awt.Color.YELLOW);
        when(r.getPosition()).thenReturn(4);
        when(g.getRoles()).thenReturn(List.of(r));

        var roles = service.getGatchaRoles("g3");
        assertEquals(1, roles.size());
        assertEquals("Gold", roles.get(0).displayName());
        assertNull(roles.get(0).rarity());
    }

    @Test
    @DisplayName("getGatchaRoles: preserves mixed-case rarity token")
    void getGatchaRoles_mixedCaseRarity() {
        Guild g = mock(Guild.class);
        when(jda.getGuildById("g4")).thenReturn(g);

        Role r = mock(Role.class);
        when(r.getName()).thenReturn("gacha:Rare:Blueish");
        when(r.getId()).thenReturn("rC");
        when(r.getColor()).thenReturn(java.awt.Color.BLUE);
        when(r.getPosition()).thenReturn(6);
        when(g.getRoles()).thenReturn(List.of(r));

        var roles = service.getGatchaRoles("g4");
        assertEquals(1, roles.size());
        assertEquals("Blueish", roles.get(0).displayName());
        assertEquals("Rare", roles.get(0).rarity());
    }

    @Test
    @DisplayName("checkRoleHierarchy reports highest gacha position")
    void checkRoleHierarchy_reportsHighestGachaPos() {
        Guild g = mock(Guild.class);
        net.dv8tion.jda.api.entities.SelfMember bot = mock(net.dv8tion.jda.api.entities.SelfMember.class);
        Role botRole = mock(Role.class);
        Role g1 = mock(Role.class);
        Role g2 = mock(Role.class);

        when(jda.getGuildById("gH")).thenReturn(g);
        when(g.getSelfMember()).thenReturn(bot);
        when(bot.getRoles()).thenReturn(List.of(botRole));
        when(botRole.getName()).thenReturn("Bot");
        when(botRole.getPosition()).thenReturn(5);
        when(g.getRoles()).thenReturn(List.of(g1, g2));
        when(g1.getName()).thenReturn("gacha:rare:A");
        when(g2.getName()).thenReturn("gacha:epic:B");
        when(g1.getPosition()).thenReturn(3);
        when(g2.getPosition()).thenReturn(8);

        var status = service.checkRoleHierarchy("gH");
        assertEquals(8, status.highestGachaRolePosition());
        // Bot below g2 -> not valid
        assertFalse(status.isValid());
    }

    @Test
    @DisplayName("cleanupOldGachaRoles delegates to deleteRolesByPrefix")
    void cleanupOldGachaRoles_delegates() {
        AdminService spy = spy(service);
        doReturn(3).when(spy).deleteRolesByPrefix("g1", "gacha:");
        int deleted = spy.cleanupOldGachaRoles("g1");
        assertEquals(3, deleted);
    }

    @Test
    @DisplayName("evictGuildsCache uses access token from authorized client")
    void evictGuildsCache_usesToken() {
        Authentication auth = mockAuth("user123");
        mockAccessToken("user123", "token-abc");
        service.evictGuildsCache(auth);
        verify(guildsCache).evictForToken("token-abc");
    }

    @Test
    @DisplayName("createGatchaRole: throws when guild missing")
    void createGatchaRole_guildMissing() {
        when(jda.getGuildById("none")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () ->
            service.createGatchaRole("none", new com.discordbot.web.dto.CreateRoleRequest("C","rare","#ffffff", null, null))
        );
    }

    @Test
    @DisplayName("createGatchaRole: creation failure rethrows with message")
    void createGatchaRole_createThrows() {
        Guild g = mock(Guild.class);
        net.dv8tion.jda.api.requests.restaction.RoleAction action = mock(net.dv8tion.jda.api.requests.restaction.RoleAction.class, RETURNS_SELF);
        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("G");
        when(g.createRole()).thenReturn(action);
        when(action.complete()).thenThrow(new RuntimeException("Unexpected error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            service.createGatchaRole("g1", new com.discordbot.web.dto.CreateRoleRequest("X","rare","#ffffff", null, null))
        );
        assertTrue(ex.getMessage().contains("Failed to create role"));
    }

    @Test
    @DisplayName("createBulkGatchaRoles: evicts cache after processing")
    void createBulk_evictsCache() {
        AdminService spy = spy(service);
        Guild guild = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getRolesByName(anyString(), anyBoolean())).thenReturn(Collections.emptyList());

        doReturn(new GachaRoleInfo("id1", "gacha:rare:A", "A", "rare", "#FFFFFF", 1))
                .when(spy).createGatchaRole(eq("g1"), any());

        var reqs = List.of(
                    new com.discordbot.web.dto.CreateRoleRequest("A", "rare", "#FFFFFF", null, null)
        );

        spy.createBulkGatchaRoles("g1", reqs);
        verify(guildsCache, times(1)).evictAll();
    }

    @Test
    @DisplayName("createGatchaRole: null/empty color defaults to white")
    void createGatchaRole_nullOrEmptyColor_defaultsWhite() {
        Guild g = mock(Guild.class);
        net.dv8tion.jda.api.requests.restaction.RoleAction action = mock(net.dv8tion.jda.api.requests.restaction.RoleAction.class, RETURNS_SELF);
        Role created = mock(Role.class);
        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("G");
        when(g.createRole()).thenReturn(action);
        when(action.complete()).thenReturn(created);
        when(created.getId()).thenReturn("rid");
        when(created.getName()).thenReturn("gacha:rare:White");
        when(created.getColor()).thenReturn(java.awt.Color.WHITE);
        when(created.getPosition()).thenReturn(1);

        // null color
        var dto1 = service.createGatchaRole("g1", new com.discordbot.web.dto.CreateRoleRequest("White","rare",null, null, null));
        assertEquals("rid", dto1.id());
        verify(action, atLeastOnce()).setColor(eq(java.awt.Color.WHITE));

        // empty color
        var dto2 = service.createGatchaRole("g1", new com.discordbot.web.dto.CreateRoleRequest("White","rare","", null, null));
        assertEquals("rid", dto2.id());
        verify(action, atLeastOnce()).setColor(eq(java.awt.Color.WHITE));
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
