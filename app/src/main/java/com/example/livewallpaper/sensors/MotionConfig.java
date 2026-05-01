package com.example.livewallpaper.sensors;

import android.content.Context;

/**
 * Wrapper class for ConfigManager to provide a consistent API for motion configuration.
 * This class delegates all calls to ConfigManager.
 */
public class MotionConfig {
    public static final String OVERRIDE_AUTO = ConfigManager.OVERRIDE_AUTO;

    /**
     * Initialize MotionConfig with a context. Must be called once at app startup.
     *
     * @param context the application context
     */
    public static void initialize(Context context) {
        ConfigManager.initialize(context);
    }

    /**
     * Check if scroll motion is currently enabled.
     *
     * @return true if scroll motion is enabled, false otherwise
     */
    public static boolean isScrollMotionEnabled() {
        return ConfigManager.isScrollMotionEnabled();
    }

    /**
     * Set whether scroll motion should be enabled.
     *
     * @param enabled true to enable scroll motion, false to disable
     */
    public static void setScrollMotionEnabled(boolean enabled) {
        ConfigManager.setScrollMotionEnabled(enabled);
    }

    /**
     * Check if gyro motion is currently enabled.
     *
     * @return true if gyro motion is enabled, false otherwise
     */
    public static boolean isGyroMotionEnabled() {
        return ConfigManager.isGyroMotionEnabled();
    }

    /**
     * Set whether gyro motion should be enabled.
     *
     * @param enabled true to enable gyro motion, false to disable
     */
    public static void setGyroMotionEnabled(boolean enabled) {
        ConfigManager.setGyroMotionEnabled(enabled);
    }

    /**
     * Get the current time of day override.
     *
     * @return the override value
     */
    public static String getTimeOfDayOverride() {
        return ConfigManager.getTimeOfDayOverride();
    }

    /**
     * Set the time of day override.
     *
     * @param override the override value
     */
    public static void setTimeOfDayOverride(String override) {
        ConfigManager.setTimeOfDayOverride(override);
    }
}
