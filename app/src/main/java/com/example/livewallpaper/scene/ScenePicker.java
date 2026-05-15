package com.example.livewallpaper.scene;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.sensors.ConfigManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Selects the next scene based on the current time of day and the scene's startTime/endTime range.
 * A scene is available when the current minute-of-day (0–1439) falls within [startTime, endTime].
 * For overnight ranges (e.g. startTime=1320 [22:00], endTime=360 [06:00]), the check wraps around midnight.
 */
public class ScenePicker {
    private static final String TAG = "ScenePicker";

    private final List<Scene> scenes;
    private final Random random;

    /**
     * Constructor
     *
     * @param scenes list of all available scenes
     */
    public ScenePicker(List<Scene> scenes) {
        this.scenes = new ArrayList<>(scenes);
        this.random = new Random();
    }

    /**
     * Get the next scene based on the current time of day.
     * Filters scenes to only those whose time range covers the current hour, then selects one randomly.
     * If no scenes match, returns a random scene from all scenes.
     *
     * @return the next scene to display
     */
    public Scene getNextScene(Scene currentScene) {
        if (scenes.isEmpty()) {
            throw new IllegalStateException("No scenes available");
        }

        int currentMinute = getCurrentMinuteOfDay();
        TimberLog.d(TAG, "Current minute-of-day for scene selection: " + currentMinute);

        // Filter scenes whose time range covers the current minute of day
        List<Scene> viableScenes = new ArrayList<>();
        for (Scene scene : scenes) {
            if (isSceneAvailable(scene, currentMinute) && !Objects.equals(scene.getSceneName(), currentScene.getSceneName())) {
                viableScenes.add(scene);
            }
        }

        // If we have viable scenes for this time, pick one randomly
        if (!viableScenes.isEmpty()) {
            Scene selected = viableScenes.get(random.nextInt(viableScenes.size()));
            TimberLog.d(TAG, "Selected scene for minute " + currentMinute + ": " + selected.getSceneName());
            return selected;
        }

        // Fallback: if no scenes match, pick a random scene from all scenes
        TimberLog.w(TAG, "No scenes found for minute " + currentMinute + ", selecting random scene");
        return scenes.get(random.nextInt(scenes.size()));
    }

    /**
     * Check whether a scene is available at the given minute-of-day (0–1439).
     * Handles both normal ranges (e.g. 540–1080 for 09:00–18:00) and overnight ranges
     * (e.g. 1320–360 for 22:00–06:00).
     */
    public static boolean isSceneAvailable(Scene scene, int minuteOfDay) {
        int start = scene.getStartTime();
        int end = scene.getEndTime();
        if (start <= end) {
            return minuteOfDay >= start && minuteOfDay <= end;
        } else {
            // Overnight: available when minuteOfDay >= start OR minuteOfDay <= end
            return minuteOfDay >= start || minuteOfDay <= end;
        }
    }

    /**
     * Get the current minute-of-day (0–1439), respecting the override in ConfigManager if set.
     * The override can be a numeric minute-of-day string (e.g. "840" for 14:00) or ConfigManager.OVERRIDE_AUTO.
     */
    private int getCurrentMinuteOfDay() {
        String override = ConfigManager.getTimeOfDayOverride();
        if (override != null && !override.equals(ConfigManager.OVERRIDE_AUTO)) {
            try {
                int minute = Integer.parseInt(override);
                if (minute >= 0 && minute <= 1439) {
                    return minute;
                }
            } catch (NumberFormatException e) {
                TimberLog.e(TAG, "Invalid minute-of-day override: " + override, e);
            }
        }
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }
}
