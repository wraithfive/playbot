package com.discordbot;

import com.discordbot.discord.DiscordApiClient;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ColorSwatchRenderTest {

    private byte[] invokeRenderSingleColorSwatch(Role role, DiscordApiClient.RoleColors colors) throws Exception {
        SlashCommandHandler handler = new SlashCommandHandler(
            null, // UserCooldownRepository
            null, // QotdStreamRepository
            null, // GuildsCache
            null, // WebSocketNotificationService
            null, // QotdSubmissionService
            null, // DiscordApiClient
            null, // CharacterAutocompleteHandler
            null  // BattleService
        );
        Method m = SlashCommandHandler.class.getDeclaredMethod(
            "renderSingleColorSwatch",
            Role.class,
            DiscordApiClient.RoleColors.class
        );
        m.setAccessible(true);
        return (byte[]) m.invoke(handler, role, colors);
    }

    @Test
    @DisplayName("renderSingleColorSwatch returns image bytes for gradient RoleColors")
    void testRenderGradientSwatch() throws Exception {
        // primary + secondary set makes a gradient
        var rc = DiscordApiClient.RoleColors.testInstance(
            0x3366FF, // primary
            0x66FFCC, // secondary
            DiscordApiClient.RoleColors.COLOR_NOT_SET // tertiary
        );
        byte[] bytes = invokeRenderSingleColorSwatch(null, rc);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Expected non-empty PNG bytes for gradient swatch");
    }

    @Test
    @DisplayName("renderSingleColorSwatch returns image bytes for holographic RoleColors")
    void testRenderHolographicSwatch() throws Exception {
        // primary + secondary + tertiary set makes holographic
        var rc = DiscordApiClient.RoleColors.testInstance(
            DiscordApiClient.RoleColors.HOLOGRAPHIC_PRIMARY,
            DiscordApiClient.RoleColors.HOLOGRAPHIC_SECONDARY,
            DiscordApiClient.RoleColors.HOLOGRAPHIC_TERTIARY
        );
        byte[] bytes = invokeRenderSingleColorSwatch(null, rc);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Expected non-empty PNG bytes for holographic swatch");
    }
}
