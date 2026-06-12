package com.example.livewallpaper.scene;

import android.content.Context;
import com.example.livewallpaper.logging.TimberLog;

import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SceneData;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteData;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads scene configurations from JSON files in the assets folder or persistent storage.
 */
public class SceneLoader {
    private static final String TAG = "SceneLoader";
    private static final String DEFAULT_SCENES_FOLDER = "scenes";

    private final Context context;
    private final Gson gson;
    private String persistentScenesPath; // Path to persistent scenes directory
    private String texturesPath;          // Path to downloaded textures directory
    private String assetsFolder = DEFAULT_SCENES_FOLDER;

    public SceneLoader(Context context) {
        this.context = context;
        this.gson = new Gson();
    }

    /**
     * Set the folder in assets to load scenes from. Defaults to "scenes".
     *
     * @param assetsFolder the folder name in assets
     */
    public void setAssetsFolder(String assetsFolder) {
        this.assetsFolder = assetsFolder;
        TimberLog.d(TAG, "Assets scenes folder set to: " + assetsFolder);
    }

    /**
     * Set the path to the persistent scenes directory.
     * When set, scenes will be loaded from this directory instead of assets.
     *
     * @param persistentPath the absolute path to the persistent scenes directory
     */
    public void setPersistentScenesPath(String persistentPath) {
        this.persistentScenesPath = persistentPath;
        TimberLog.d(TAG, "Persistent scenes path set to: " + persistentPath);
    }

    /**
     * Set the path to the downloaded textures directory.
     * When a sprite's textureResource starts with "/uploads/", the loader will
     * look for the file in this directory.
     *
     * @param texturesPath the absolute path to the textures directory
     */
    public void setTexturesPath(String texturesPath) {
        this.texturesPath = texturesPath;
        TimberLog.d(TAG, "Textures path set to: " + texturesPath);
    }

    /**
     * Load a scene from a JSON file.
     * First attempts to load from persistent storage if a path is set,
     * otherwise falls back to assets folder.
     *
     * @param fileName the name of the JSON file (e.g., "girl_back.json")
     * @return a Scene object populated with sprites from the JSON data
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the JSON is invalid or missing required fields
     */
    public Scene loadScene(String fileName) throws IOException {
        TimberLog.d(TAG, "Loading scene: " + fileName);

        SceneData sceneData;
        if (persistentScenesPath != null) {
            // Load from persistent storage
            File sceneFile = new File(persistentScenesPath, fileName);
            if (sceneFile.exists()) {
                TimberLog.d(TAG, "Loading from persistent storage: " + sceneFile.getAbsolutePath());
                sceneData = parseSceneDataFromFile(sceneFile);
            } else {
                TimberLog.d(TAG, "File not found in persistent storage, falling back to assets: " + fileName);
                String filePath = assetsFolder + "/" + fileName;
                sceneData = parseSceneDataFromAssets(filePath);
            }
        } else {
            // Fall back to assets
            String filePath = assetsFolder + "/" + fileName;
            TimberLog.d(TAG, "Loading from assets: " + filePath);
            sceneData = parseSceneDataFromAssets(filePath);
        }

        validateSceneData(sceneData, fileName);

        // Derive scene name from filename (remove .json extension if present)
        String sceneName = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;

        Scene scene = new Scene(sceneName);
        applySceneSettings(scene, sceneData);
        populateSprites(scene, sceneData, sceneName);

        TimberLog.d(TAG, "Successfully loaded scene '" + scene.getSceneName() + "' with "
            + scene.getSprites().size() + " sprites");

        return scene;
    }

    /**
     * Parse JSON file from persistent storage into SceneData object.
     */
    private SceneData parseSceneDataFromFile(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, SceneData.class);
        }
    }

    /**
     * Parse JSON file from assets into SceneData object.
     */
    private SceneData parseSceneDataFromAssets(String filePath) throws IOException {
        try (InputStream inputStream = context.getAssets().open(filePath);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, SceneData.class);
        }
    }

    /**
     * Validate that SceneData contains required fields.
     */
    private void validateSceneData(SceneData sceneData, String filePath) {
        if (sceneData == null) {
            throw new IllegalArgumentException("Failed to parse JSON from: " + filePath);
        }

        if (sceneData.sprites == null) {
            throw new IllegalArgumentException("Sprites array is required in JSON: " + filePath);
        }
    }

    /**
     * Apply scene-level settings like xFocus and time range.
     */
    private void applySceneSettings(Scene scene, SceneData sceneData) {
        if (sceneData.xFocus >= 0.0f && sceneData.xFocus <= 1.0f) {
            scene.setXFocus(sceneData.xFocus);
        }
        scene.setStartTime(sceneData.startTime);
        scene.setEndTime(sceneData.endTime);
    }

    /**
     * Create and add sprites to the scene from sprite data.
     */
    private void populateSprites(Scene scene, SceneData sceneData, String sceneName) {
        for (SpriteData spriteData : sceneData.sprites) {
            Sprite sprite = createSpriteFromData(spriteData, sceneName);
            if (sprite != null) {
                scene.addSprite(sprite);
            }
        }
    }

    /**
     * Create a single sprite from SpriteData, or return null if creation fails.
     */
    private Sprite createSpriteFromData(SpriteData spriteData, String sceneName) {
        if (spriteData.textureResource == null || spriteData.textureResource.isEmpty()) {
            TimberLog.w(TAG, "Skipping sprite with missing textureResource in scene: " + sceneName);
            return null;
        }

        // Uploaded image: textureResource is "/uploads/<filename>"
        if (spriteData.textureResource.startsWith("/uploads/") && texturesPath != null) {
            String filename = spriteData.textureResource.substring("/uploads/".length());
            java.io.File textureFile = new java.io.File(texturesPath, filename);
            // Always include the sprite even if the file isn't accessible right now (e.g. at
            // boot before credential storage is unlocked). The async texture loader will fail
            // gracefully with texId=0 (sprite invisible), and reloadTextures() on the next
            // visibility change will retry once storage is available.
            if (!textureFile.exists()) {
                TimberLog.w(TAG, "Downloaded texture file not found at load time: " + textureFile.getAbsolutePath() + ". Will retry on next surface creation.");
            }
            spriteData.textureResourceId = -1; // Marker: resolve via file path
            spriteData.textureResource = textureFile.getAbsolutePath();
            spriteData.name = filename;
            return new Sprite(spriteData);
        }

        int resourceId = getDrawableResourceId(spriteData.textureResource);
        if (resourceId == 0) {
            TimberLog.w(TAG, "Could not find drawable resource: " + spriteData.textureResource + ". Skipping sprite.");
            return null;
        }

        TimberLog.d(TAG, "Successfully resolved texture: " + spriteData.textureResource + " -> resourceId=" + resourceId);

        // Set the resolved resource ID and name on the sprite data
        spriteData.textureResourceId = resourceId;
        spriteData.name = spriteData.textureResource;

        return new Sprite(spriteData);
    }

    /**
     * Get the drawable resource ID from a resource name.
     *
     * @param resourceName the name of the drawable (e.g., "knight", "tower")
     * @return the resource ID, or 0 if not found
     */
    private int getDrawableResourceId(String resourceName) {
        return context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());
    }
}
