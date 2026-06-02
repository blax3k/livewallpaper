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

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Persist the scenes and textures directory paths for the active project. */
    public static void setActiveProjectDirs(Context context, String scenesDir, String texturesDir) {
        getPrefs(context).edit()
                .putString(KEY_ACTIVE_SCENES, scenesDir)
                .putString(KEY_ACTIVE_TEXTURES, texturesDir)
                .apply();
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
