package com.discordbot;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.RoleColors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ColorSwatchRenderTest {

    private byte[] invokeRenderSingleColorSwatch(Role role) throws Exception {
        SlashCommandHandler handler = new SlashCommandHandler(
            null, // UserCooldownRepository
            null, // QotdStreamRepository
            null, // GuildsCache
            null, // WebSocketNotificationService
            null  // QotdSubmissionService
        );
        Method m = SlashCommandHandler.class.getDeclaredMethod(
            "renderSingleColorSwatch",
            Role.class
        );
        m.setAccessible(true);
        return (byte[]) m.invoke(handler, role);
    }

    @Test
    @DisplayName("renderSingleColorSwatch returns image bytes for gradient RoleColors")
    void testRenderGradientSwatch() throws Exception {
        // Create mock role with gradient colors (primary + secondary)
        Role mockRole = mock(Role.class);
        RoleColors mockColors = mock(RoleColors.class);
        
        when(mockRole.getColors()).thenReturn(mockColors);
        when(mockColors.isGradient()).thenReturn(true);
        when(mockColors.isHolographic()).thenReturn(false);
        when(mockColors.getPrimary()).thenReturn(new Color(0x3366FF));
        when(mockColors.getSecondary()).thenReturn(new Color(0x66FFCC));
        when(mockColors.getTertiary()).thenReturn(null);
        
        byte[] bytes = invokeRenderSingleColorSwatch(mockRole);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Expected non-empty PNG bytes for gradient swatch");
    }

    @Test
    @DisplayName("renderSingleColorSwatch returns image bytes for holographic RoleColors")
    void testRenderHolographicSwatch() throws Exception {
        // Create mock role with holographic colors (primary + secondary + tertiary)
        Role mockRole = mock(Role.class);
        RoleColors mockColors = mock(RoleColors.class);
        
        when(mockRole.getColors()).thenReturn(mockColors);
        when(mockColors.isGradient()).thenReturn(false);
        when(mockColors.isHolographic()).thenReturn(true);
        when(mockColors.getPrimary()).thenReturn(new Color(11127295));
        when(mockColors.getSecondary()).thenReturn(new Color(16759788));
        when(mockColors.getTertiary()).thenReturn(new Color(16761760));
        
        byte[] bytes = invokeRenderSingleColorSwatch(mockRole);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Expected non-empty PNG bytes for holographic swatch");
    }
}
