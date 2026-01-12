package com.discordbot.battle.service;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BattlePermissionService
 * Phase 11: Security & Permissions
 */
@SuppressWarnings("null")
class BattlePermissionServiceTest {

    private BattlePermissionService permissionService;
    private Guild guild;
    private Member member;

    @BeforeEach
    void setUp() {
        permissionService = new BattlePermissionService();
        guild = mock(Guild.class);
        member = mock(Member.class);
    }

    @Test
    void testHasAdminPermissionWithAdministrator() {
        when(guild.getMemberById("user123")).thenReturn(member);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(false);

        assertTrue(permissionService.hasAdminPermission(guild, "user123"),
            "User with ADMINISTRATOR permission should have admin access");
    }

    @Test
    void testHasAdminPermissionWithManageServer() {
        when(guild.getMemberById("user123")).thenReturn(member);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);

        assertTrue(permissionService.hasAdminPermission(guild, "user123"),
            "User with MANAGE_SERVER permission should have admin access");
    }

    @Test
    void testHasAdminPermissionWithBothPermissions() {
        when(guild.getMemberById("user123")).thenReturn(member);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);

        assertTrue(permissionService.hasAdminPermission(guild, "user123"),
            "User with both permissions should have admin access");
    }

    @Test
    void testHasAdminPermissionWithNoPermissions() {
        when(guild.getMemberById("user123")).thenReturn(member);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(false);

        assertFalse(permissionService.hasAdminPermission(guild, "user123"),
            "User without admin permissions should not have admin access");
    }

    @Test
    void testHasAdminPermissionWithNullGuild() {
        assertFalse(permissionService.hasAdminPermission(null, "user123"),
            "Null guild should return false");
    }

    @Test
    void testHasAdminPermissionWithMemberNotFound() {
        when(guild.getMemberById("user123")).thenReturn(null);
        when(guild.getId()).thenReturn("guild123");

        assertFalse(permissionService.hasAdminPermission(guild, "user123"),
            "Member not found in guild should return false");
    }

    @Test
    void testGetPermissionDeniedMessage() {
        String message = permissionService.getPermissionDeniedMessage();

        assertNotNull(message, "Permission denied message should not be null");
        assertTrue(message.contains("permission"), "Message should mention permission");
        assertTrue(message.contains("Administrator") || message.contains("Manage Server"),
            "Message should mention required permissions");
    }
}
