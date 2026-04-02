package com.example.livewallpaper.sensors;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.livewallpaper.logging.TimberLog;

/**
 * Thread-safe configuration class for sharing motion settings between
 * MainActivity and the wallpaper service (render thread).
 * Settings are persisted to SharedPreferences and survive app restarts.
 */
public class ConfigManager {
    private static final String TAG = "MotionConfig";
    private static final String PREFS_NAME = "motion_config";
    private static final String KEY_SCROLL_MOTION = "scroll_motion_enabled";
    private static final String KEY_GYRO_MOTION = "gyro_motion_enabled";
    private static final String KEY_TIME_OF_DAY_OVERRIDE = "time_of_day_override";

    private static final boolean DEFAULT_SCROLL_MOTION = true;
    private static final boolean DEFAULT_GYRO_MOTION = true;
    public static final String OVERRIDE_AUTO = "AUTO";

    private static volatile boolean scrollMotionEnabled = DEFAULT_SCROLL_MOTION;
    private static volatile boolean gyroMotionEnabled = DEFAULT_GYRO_MOTION;
    private static volatile String timeOfDayOverride = OVERRIDE_AUTO;
    private static SharedPreferences sharedPreferences = null;

    /**
     * Initialize MotionConfig with a context. Must be called once at app startup.
     * This loads persisted settings from SharedPreferences.
     *
     * @param context the application context
     */
    public static void initialize(Context context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // Load settings from persistent storage
            scrollMotionEnabled = sharedPreferences.getBoolean(KEY_SCROLL_MOTION, DEFAULT_SCROLL_MOTION);
            gyroMotionEnabled = sharedPreferences.getBoolean(KEY_GYRO_MOTION, DEFAULT_GYRO_MOTION);
            timeOfDayOverride = sharedPreferences.getString(KEY_TIME_OF_DAY_OVERRIDE, OVERRIDE_AUTO);
            TimberLog.d(TAG, "MotionConfig initialized - Scroll: " + scrollMotionEnabled + ", Gyro: " + gyroMotionEnabled + ", TimeOverride: " + timeOfDayOverride);
        }
    }

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
     * Changes are persisted to SharedPreferences.
     *
     * @param enabled true to enable scroll motion, false to disable
     */
    public static void setScrollMotionEnabled(boolean enabled) {
        scrollMotionEnabled = enabled;
        persistSetting(KEY_SCROLL_MOTION, enabled);
        TimberLog.d(TAG, "Scroll motion set to: " + enabled);
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
     * Changes are persisted to SharedPreferences.
     *
     * @param enabled true to enable gyro motion, false to disable
     */
    public static void setGyroMotionEnabled(boolean enabled) {
        gyroMotionEnabled = enabled;
        persistSetting(KEY_GYRO_MOTION, enabled);
        TimberLog.d(TAG, "Gyro motion set to: " + enabled);
    }

    /**
     * Get the current time of day override.
     *
     * @return the override value (AUTO, DAWN, DAY, SUNSET, NIGHT)
     */
    public static String getTimeOfDayOverride() {
        return timeOfDayOverride;
    }

    /**
     * Set the time of day override.
     *
     * @param override the override value (AUTO, DAWN, DAY, SUNSET, NIGHT)
     */
    public static void setTimeOfDayOverride(String override) {
        timeOfDayOverride = override;
        if (sharedPreferences != null) {
            sharedPreferences.edit().putString(KEY_TIME_OF_DAY_OVERRIDE, override).apply();
        }
        TimberLog.d(TAG, "Time of day override set to: " + override);
    }

    /**
     * Persist a setting to SharedPreferences.
     *
     * @param key the preference key
     * @param value the boolean value to persist
     */
    private static void persistSetting(String key, boolean value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putBoolean(key, value).apply();
        } else {
            TimberLog.w(TAG, "SharedPreferences not initialized, setting not persisted: " + key);
        }
    }
}
