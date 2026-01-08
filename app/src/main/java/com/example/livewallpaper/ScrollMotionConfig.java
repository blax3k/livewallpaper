package com.example.livewallpaper;

/**
 * Configuration class to share scroll motion state between MainActivity and the wallpaper service.
 * This allows the UI to control whether scroll motion is enabled or disabled.
 */
public class ScrollMotionConfig {
    private static volatile boolean scrollMotionEnabled = true;

    /**
     * Check if scroll motion is currently enabled.
     *
     * @return true if scroll motion is enabled, false otherwise
     */
    public static boolean isScrollMotionEnabled() {
        return scrollMotionEnabled;
    }

    /**
     * Set whether scroll motion should be enabled.
     *
     * @param enabled true to enable scroll motion, false to disable
     */
    public static void setScrollMotionEnabled(boolean enabled) {
        scrollMotionEnabled = enabled;
    }
}

