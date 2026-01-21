package com.example.livewallpaper.scene;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads scene configurations from JSON files in the assets folder.
 */
public class SceneLoader {
    private static final String TAG = "SceneLoader";
    private static final String SCENES_FOLDER = "scenes";

    private final Context context;
    private final Gson gson;

    public SceneLoader(Context context) {
        this.context = context;
        this.gson = new Gson();
    }

    /**
     * Load a scene from a JSON file in the assets/scenes folder.
     *
     * @param fileName the name of the JSON file (e.g., "girl_back.json")
     * @return a Scene object populated with sprites from the JSON data
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the JSON is invalid or missing required fields
     */
    public Scene loadScene(String fileName) throws IOException {
        String filePath = SCENES_FOLDER + "/" + fileName;
        Log.d(TAG, "Loading scene from: " + filePath);

        SceneData sceneData = parseSceneData(filePath);
        validateSceneData(sceneData, filePath);

        Scene scene = new Scene(sceneData.sceneName);
        applySceneSettings(scene, sceneData);
        populateSprites(scene, sceneData);

        Log.d(TAG, "Successfully loaded scene '" + scene.getSceneName() + "' with "
            + scene.getSprites().size() + " sprites");

        return scene;
    }

    /**
     * Parse JSON file into SceneData object.
     */
    private SceneData parseSceneData(String filePath) throws IOException {
        InputStream inputStream = context.getAssets().open(filePath);
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        SceneData sceneData = gson.fromJson(reader, SceneData.class);
        reader.close();
        return sceneData;
    }

    /**
     * Validate that SceneData contains required fields.
     */
    private void validateSceneData(SceneData sceneData, String filePath) {
        if (sceneData == null) {
            throw new IllegalArgumentException("Failed to parse JSON from: " + filePath);
        }

        if (sceneData.sceneName == null || sceneData.sceneName.isEmpty()) {
            throw new IllegalArgumentException("Scene name is required in JSON: " + filePath);
        }

        if (sceneData.sprites == null) {
            throw new IllegalArgumentException("Sprites array is required in JSON: " + filePath);
        }
    }

    /**
     * Apply scene-level settings like xFocus.
     */
    private void applySceneSettings(Scene scene, SceneData sceneData) {
        if (sceneData.xFocus >= 0.0f && sceneData.xFocus <= 1.0f) {
            scene.setXFocus(sceneData.xFocus);
        }
    }

    /**
     * Create and add sprites to the scene from sprite data.
     */
    private void populateSprites(Scene scene, SceneData sceneData) {
        for (SpriteData spriteData : sceneData.sprites) {
            Sprite sprite = createSpriteFromData(spriteData, sceneData.sceneName);
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
            Log.w(TAG, "Skipping sprite with missing textureResource in scene: " + sceneName);
            return null;
        }

        int resourceId = getDrawableResourceId(spriteData.textureResource);
        if (resourceId == 0) {
            Log.w(TAG, "Could not find drawable resource: " + spriteData.textureResource + ". Skipping sprite.");
            Log.w(TAG, "Tried to resolve: package=" + context.getPackageName() + ", name=" + spriteData.textureResource);
            return null;
        }

        Log.d(TAG, "Successfully resolved texture: " + spriteData.textureResource + " -> resourceId=" + resourceId);

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
        try {
            Class<?> drawables = Class.forName(context.getPackageName() + ".R$drawable");
            java.lang.reflect.Field field = drawables.getField(resourceName);
            return field.getInt(null);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            Log.w(TAG, "Drawable resource not found via R.drawable lookup: " + resourceName, e);
            return 0;
        }
    }
}

