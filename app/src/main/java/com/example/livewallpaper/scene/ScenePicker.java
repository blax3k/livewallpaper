package com.example.livewallpaper.scene;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SceneData;
import com.example.livewallpaper.sensors.MotionConfig;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Selects the next scene based on the current time of day and scene's timeOfDay properties.
 * Uses device time to determine which time-of-day period we're in, then filters scenes accordingly.
 *
 * Time periods:
 * - Dawn: 06:00 - 09:00
 * - Day: 09:00 - 18:00
 * - Sunset: 18:00 - 21:00
 * - Night: 21:00 - 06:00
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
     * Filters scenes to only those matching the current time period, then selects one randomly.
     * If no scenes match the current time period, returns a random scene from all scenes.
     *
     * @return the next scene to display
     */
    public Scene getNextScene(Scene currentScene) {
        if (scenes.isEmpty()) {
            throw new IllegalStateException("No scenes available");
        }

        SceneData.TimeOfDay currentTimeOfDay = getOverriddenTimeOfDay();
        TimberLog.d(TAG, "Effective time of day: " + currentTimeOfDay);

        // Filter scenes to those matching the current time of day
        List<Scene> viableScenes = new ArrayList<>();
        for (Scene scene : scenes) {
            if (scene.getTimeOfDay() == currentTimeOfDay && !Objects.equals(scene.getSceneName(), currentScene.getSceneName())) {
                viableScenes.add(scene);
            }
        }

        // If we have viable scenes for this time period, pick one randomly
        if (!viableScenes.isEmpty()) {
            Scene selected = viableScenes.get(random.nextInt(viableScenes.size()));
            TimberLog.d(TAG, "Selected scene for " + currentTimeOfDay + ": " + selected.getSceneName());
            return selected;
        }

        // Fallback: if no scenes match, pick a random scene from all scenes
        TimberLog.w(TAG, "No scenes found for " + currentTimeOfDay + ", selecting random scene");
        return scenes.get(random.nextInt(scenes.size()));
    }

    /**
     * Determine the time of day, respecting the override in MotionConfig if set.
     *
     * @return the TimeOfDay to use for scene selection
     */
    private SceneData.TimeOfDay getOverriddenTimeOfDay() {
        String override = MotionConfig.getTimeOfDayOverride();
        if (override != null && !override.equals(MotionConfig.OVERRIDE_AUTO)) {
            try {
                return SceneData.TimeOfDay.valueOf(override);
            } catch (IllegalArgumentException e) {
                TimberLog.e(TAG, "Invalid time of day override: " + override, e);
            }
        }
        return getCurrentTimeOfDay();
    }

    /**
     * Determine the current time of day based on the device's current time.
     *
     * @return the TimeOfDay that matches the current hour
     */
    private SceneData.TimeOfDay getCurrentTimeOfDay() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (hour >= 6 && hour < 9) {
            return SceneData.TimeOfDay.DAWN;
        } else if (hour >= 9 && hour < 18) {
            return SceneData.TimeOfDay.DAY;
        } else if (hour >= 18 && hour < 21) {
            return SceneData.TimeOfDay.SUNSET;
        } else {
            // 21:00 - 06:00
            return SceneData.TimeOfDay.NIGHT;
        }
    }
}
