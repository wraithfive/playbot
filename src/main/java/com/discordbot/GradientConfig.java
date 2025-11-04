package com.discordbot;

/**
 * Deprecated: Manual gradient configuration has been removed.
 * The bot now uses Discord's Role Colors Object from the API.
 *
 * This class is kept only to avoid breaking source compatibility on feature branches.
 * All methods throw UnsupportedOperationException if invoked.
 */
@Deprecated
public final class GradientConfig {
    private GradientConfig() {}

    public static boolean hasStops(String roleId) {
        throw new UnsupportedOperationException("GradientConfig is removed; use Discord Role Colors API");
    }

    public static java.util.List<java.awt.Color> getStops(String roleId) {
        throw new UnsupportedOperationException("GradientConfig is removed; use Discord Role Colors API");
    }
}
