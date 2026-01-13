package com.discordbot;

import com.discordbot.discord.DiscordApiClient;
import com.discordbot.web.dto.BulkRoleDeletionResult;
import com.discordbot.web.dto.RoleDeletionResult;
import com.discordbot.web.dto.RoleHierarchyStatus;
import com.discordbot.web.service.AdminService;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class AdminServiceBranchesTest {

    private JDA jda;
    private OAuth2AuthorizedClientService clients;
    private GuildsCache cache;
    private WebSocketNotificationService ws;
    private AdminService service;

    @BeforeEach
    void setup() {
        jda = mock(JDA.class);
        clients = mock(OAuth2AuthorizedClientService.class);
        cache = mock(GuildsCache.class);
        ws = mock(WebSocketNotificationService.class);
        DiscordApiClient discordApiClient = mock(DiscordApiClient.class);
        service = new AdminService(jda, clients, cache, ws, discordApiClient);
    }

    @Test
    @DisplayName("leaveBotFromGuild: throws for missing guild")
    void leaveBotFromGuild_missingGuild() {
        when(jda.getGuildById("g1")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.leaveBotFromGuild("g1"));
    }

    @Test
    @DisplayName("leaveBotFromGuild: queues leave action")
    void leaveBotFromGuild_queues() {
        Guild guild = mock(Guild.class);
        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> leaveAction = mock(AuditableRestAction.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getName()).thenReturn("G");
        when(guild.leave()).thenReturn(leaveAction);

        // Invoke success callback immediately
    doAnswer(inv -> { java.util.function.Consumer<?> c = inv.getArgument(0); c.accept(null); return null; })
                .when(leaveAction).queue(any(), any());

        service.leaveBotFromGuild("g1");
        verify(leaveAction, times(1)).queue(any(), any());
    }

    @Test
    @DisplayName("getManageableGuilds: returns empty when no access token")
    void getManageableGuilds_noToken() {
        Authentication auth = mockAuth("user1");
        // getAccessToken -> null when client missing
        when(clients.loadAuthorizedClient(eq("discord"), eq("user1"))).thenReturn(null);
    List<?> res = service.getManageableGuilds(auth);
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("isUserAdminInGuild: true for admin perms; false otherwise")
    void isUserAdminInGuild_various() {
        Authentication auth = mockAuth("user1");
    // Prepare authorized client first to avoid nested stubbing inside thenReturn()
    var authorizedClient = TestTokens.authorizedClient("tok");
    when(clients.loadAuthorizedClient(eq("discord"), eq("user1")))
        .thenReturn(authorizedClient);

        Map<String,Object> guildAdmin = new HashMap<>();
        guildAdmin.put("id", "g1");
        guildAdmin.put("name", "G");
        guildAdmin.put("permissions", String.valueOf(net.dv8tion.jda.api.Permission.ADMINISTRATOR.getRawValue()));
        when(cache.get(eq("tok"), any())).thenReturn(List.of(guildAdmin));

        assertTrue(service.isUserAdminInGuild(auth, "g1"));

        Map<String,Object> guildNo = new HashMap<>();
        guildNo.put("id", "g2");
        guildNo.put("name", "G2");
        guildNo.put("permissions", "0");
        when(cache.get(eq("tok"), any())).thenReturn(List.of(guildNo));

        assertFalse(service.isUserAdminInGuild(auth, "g2"));
    }

    @Test
    @DisplayName("checkRoleHierarchy: handles guild missing and no bot roles")
    void checkRoleHierarchy_missingAndNoRoles() {
        when(jda.getGuildById("none")).thenReturn(null);
    RoleHierarchyStatus s1 = service.checkRoleHierarchy("none");
        assertFalse(s1.isValid());
    assertEquals("Guild not found", s1.conflictingRoles().get(0));

        Guild g = mock(Guild.class);
    SelfMember bot = mock(SelfMember.class);
        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getSelfMember()).thenReturn(bot);
        when(bot.getRoles()).thenReturn(Collections.emptyList());

        RoleHierarchyStatus s2 = service.checkRoleHierarchy("g1");
        assertFalse(s2.isValid());
        assertEquals("No role assigned", s2.botRoleName());
    }

    @Test
    @DisplayName("checkRoleHierarchy: detects conflicts and valid state")
    void checkRoleHierarchy_conflictAndValid() {
        Guild g = mock(Guild.class);
    SelfMember bot = mock(SelfMember.class);
        Role botRole = mock(Role.class);
        Role gachaHigh = mock(Role.class);
        Role gachaLow = mock(Role.class);

        when(jda.getGuildById("g2")).thenReturn(g);
        when(g.getSelfMember()).thenReturn(bot);
    when(bot.getRoles()).thenReturn(List.of(botRole));
        when(botRole.getName()).thenReturn("BotRole");
        when(botRole.getPosition()).thenReturn(1);
    when(g.getRoles()).thenReturn(List.of(gachaHigh, gachaLow));
        when(gachaHigh.getName()).thenReturn("gacha:rare:One");
        when(gachaHigh.getPosition()).thenReturn(2); // above bot per service logic
        when(gachaLow.getName()).thenReturn("gacha:common:Two");
        when(gachaLow.getPosition()).thenReturn(1);

        RoleHierarchyStatus conflict = service.checkRoleHierarchy("g2");
        assertFalse(conflict.isValid());
    assertFalse(conflict.conflictingRoles().isEmpty());

        // Valid case: move bot higher
        when(botRole.getPosition()).thenReturn(3);
        RoleHierarchyStatus ok = service.checkRoleHierarchy("g2");
        assertTrue(ok.isValid());
    assertTrue(ok.conflictingRoles().isEmpty());
    }

    @Test
    @DisplayName("createGatchaRole: invalid color defaults to white and notifies")
    void createGatchaRole_invalidColor_defaultsWhite() {
        Guild g = mock(Guild.class);
        RoleAction action = mock(RoleAction.class, RETURNS_SELF);
        Role created = mock(Role.class);
        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("G");
        when(g.createRole()).thenReturn(action);
        when(action.complete()).thenReturn(created);
        when(created.getId()).thenReturn("rid");
        when(created.getName()).thenReturn("gacha:rare:White");
        when(created.getColor()).thenReturn(Color.WHITE);
        when(created.getPosition()).thenReturn(1);

        var dto = service.createGatchaRole("g1", new com.discordbot.web.dto.CreateRoleRequest("White","rare","not-a-hex", null, null));
        assertEquals("rid", dto.id());
        verify(action, atLeastOnce()).setColor(eq(Color.WHITE));
        verify(ws, times(1)).notifyRolesChanged("g1", "created");
    }

    @Test
    @DisplayName("deleteRolesByPrefix: deletes only matching roles")
    void deleteRolesByPrefix_onlyMatching() {
        Guild g = mock(Guild.class);
        Role r1 = mock(Role.class, RETURNS_DEEP_STUBS);
        Role r2 = mock(Role.class, RETURNS_DEEP_STUBS);
        when(jda.getGuildById("g1")).thenReturn(g);
        when(g.getName()).thenReturn("G");
    when(g.getRoles()).thenReturn(List.of(r1, r2));
        when(r1.getName()).thenReturn("gacha:old:Red");
        when(r2.getName()).thenReturn("Member");

        int deleted = service.deleteRolesByPrefix("g1", "gacha:");
        assertEquals(1, deleted);
        verify(r1, times(1)).delete();
        verify(r2, never()).delete();
    }

    @Test
    @DisplayName("deleteGatchaRole: guild missing")
    void deleteGatchaRole_guildMissing() {
        when(jda.getGuildById("g1")).thenReturn(null);
        RoleDeletionResult res = service.deleteGatchaRole("g1", "r1");
        assertFalse(res.success());
        assertEquals("Guild not found", res.error());
    }

    @Test
    @DisplayName("deleteBulkGatchaRoles: guild missing returns errors")
    void deleteBulk_guildMissing() {
        when(jda.getGuildById("g1")).thenReturn(null);
    BulkRoleDeletionResult res = service.deleteBulkGatchaRoles("g1", List.of("r1","r2"));
        assertEquals(0, res.successCount());
        assertEquals(2, res.failureCount());
        assertFalse(res.errors().isEmpty());
    }

    @Test
    @DisplayName("getManageableGuilds: returns empty when principal not OAuth2User")
    void getManageableGuilds_principalNotOAuth2User() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-a-user");
        List<?> res = service.getManageableGuilds(auth);
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("getManageableGuilds: returns empty when OAuth2User missing id attribute")
    void getManageableGuilds_userIdMissing() {
        Authentication auth = mock(Authentication.class);
        Map<String,Object> attrs = new HashMap<>();
        // intentionally omit "id" but include a different attribute to satisfy constructor
        attrs.put("username", "userX");
        OAuth2User user = new DefaultOAuth2User(Collections.emptyList(), attrs, "username");
        when(auth.getPrincipal()).thenReturn(user);
        List<?> res = service.getManageableGuilds(auth);
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("isUserAdminInGuild: returns false when access token is missing")
    void isUserAdminInGuild_noToken() {
        Authentication auth = mockAuth("user-x");
        // no authorized client -> getAccessToken returns null
        when(clients.loadAuthorizedClient(eq("discord"), eq("user-x"))).thenReturn(null);
        assertFalse(service.isUserAdminInGuild(auth, "g1"));
    }

    @Test
    @DisplayName("getGatchaRoles: returns empty when guild missing")
    void getGatchaRoles_guildMissing() {
        when(jda.getGuildById("none")).thenReturn(null);
        var roles = service.getGatchaRoles("none");
        assertNotNull(roles);
        assertTrue(roles.isEmpty());
    }

    @Test
    @DisplayName("deleteRolesByPrefix: throws when guild missing")
    void deleteRolesByPrefix_guildMissing() {
        when(jda.getGuildById("none")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.deleteRolesByPrefix("none", "gacha:"));
    }

    @Test
    @DisplayName("leaveBotFromGuild: error callback path is handled")
    void leaveBotFromGuild_errorCallback() {
        Guild guild = mock(Guild.class);
        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> leaveAction = mock(AuditableRestAction.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getName()).thenReturn("G");
        when(guild.leave()).thenReturn(leaveAction);

        // Invoke error callback immediately
        doAnswer(inv -> { java.util.function.Consumer<Throwable> err = inv.getArgument(1); err.accept(new RuntimeException("bad")); return null; })
            .when(leaveAction).queue(any(), any());

        assertDoesNotThrow(() -> service.leaveBotFromGuild("g1"));
        verify(leaveAction, times(1)).queue(any(), any());
    }

    // helpers
    private Authentication mockAuth(String userId) {
        Authentication auth = mock(Authentication.class);
        Map<String,Object> attrs = new HashMap<>();
        attrs.put("id", userId);
        OAuth2User user = new DefaultOAuth2User(Collections.emptyList(), attrs, "id");
        when(auth.getPrincipal()).thenReturn(user);
        when(auth.getName()).thenReturn(userId);
        return auth;
    }

    private static class TestTokens {
        static org.springframework.security.oauth2.client.OAuth2AuthorizedClient authorizedClient(String token) {
            var access = mock(org.springframework.security.oauth2.core.OAuth2AccessToken.class);
            when(access.getTokenValue()).thenReturn(token);
            var client = mock(org.springframework.security.oauth2.client.OAuth2AuthorizedClient.class);
            when(client.getAccessToken()).thenReturn(access);
            return client;
        }
    }
}
