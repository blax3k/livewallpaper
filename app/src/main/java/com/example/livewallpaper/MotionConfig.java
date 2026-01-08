package com.example.livewallpaper;

/**
 * Thread-safe configuration class for sharing motion settings between
 * MainActivity and the wallpaper service (render thread).
 */
public class MotionConfig {
    private static volatile boolean scrollMotionEnabled = true;
    private static volatile boolean gyroMotionEnabled = true;

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

    /**
     * Check if gyro motion is currently enabled.
     *
     * @return true if gyro motion is enabled, false otherwise
     */
    public static boolean isGyroMotionEnabled() {
        return gyroMotionEnabled;
    }

    /**
     * Set whether gyro motion should be enabled.
     *
     * @param enabled true to enable gyro motion, false to disable
     */
    public static void setGyroMotionEnabled(boolean enabled) {
        gyroMotionEnabled = enabled;
    }
}

