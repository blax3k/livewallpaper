package com.example.livewallpaper.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores runtime wallpaper configuration that must be shared between the dev editor
 * activities and the live wallpaper service (GLWallpaperService / SceneFileManager).
 *
 * The active project's scene and texture directory paths are written here when the
 * user taps "Set as Wallpaper", and read by SceneFileManager to point the renderer
 * at the correct per-project folder.
 */
public class WallpaperPreferences {

    private static final String PREFS_NAME          = "WallpaperConfig";
    private static final String KEY_ACTIVE_SCENES   = "active_scenes_dir";
    private static final String KEY_ACTIVE_TEXTURES = "active_textures_dir";
    private static final String KEY_CURRENT_PROJECT_ID = "current_project_id";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Persist active project data used by the wallpaper service and dev UI. */
    public static void setActiveProject(Context context, String projectId, String scenesDir, String texturesDir) {
        getPrefs(context).edit()
                .putString(KEY_ACTIVE_SCENES, scenesDir)
                .putString(KEY_ACTIVE_TEXTURES, texturesDir)
                .putString(KEY_CURRENT_PROJECT_ID, projectId)
                .apply();
    }

    /** Returns the project ID currently configured for the live wallpaper. */
    public static String getCurrentProjectId(Context context) {
        return getPrefs(context).getString(KEY_CURRENT_PROJECT_ID, null);
    }

    /**
     * Returns the active project's scenes directory path, or {@code null} if none has
     * been configured yet (wallpaper will fall back to the default bundle scenes).
     */
    public static String getActiveScenesDir(Context context) {
        return getPrefs(context).getString(KEY_ACTIVE_SCENES, null);
    }

    /**
     * Returns the active project's textures directory path, or {@code null} if none has
     * been configured yet.
     */
    public static String getActiveTexturesDir(Context context) {
        return getPrefs(context).getString(KEY_ACTIVE_TEXTURES, null);
    }
}
