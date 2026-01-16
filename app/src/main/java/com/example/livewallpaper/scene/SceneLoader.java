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

        // Read JSON from assets
        InputStream inputStream = context.getAssets().open(filePath);
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        // Parse JSON into SceneData
        SceneData sceneData = gson.fromJson(reader, SceneData.class);
        reader.close();

        if (sceneData == null) {
            throw new IllegalArgumentException("Failed to parse JSON from: " + filePath);
        }

        if (sceneData.sceneName == null || sceneData.sceneName.isEmpty()) {
            throw new IllegalArgumentException("Scene name is required in JSON: " + filePath);
        }

        if (sceneData.sprites == null) {
            throw new IllegalArgumentException("Sprites array is required in JSON: " + filePath);
        }

        // Create Scene object
        Scene scene = new Scene(sceneData.sceneName);

        // Convert each SpriteData to a Sprite and add to scene
        for (SceneData.SpriteData spriteData : sceneData.sprites) {
            if (spriteData.textureResource == null || spriteData.textureResource.isEmpty()) {
                Log.w(TAG, "Skipping sprite with missing textureResource in scene: " + sceneData.sceneName);
                continue;
            }

            // Resolve texture resource ID from resource name
            int resourceId = getDrawableResourceId(spriteData.textureResource);
            if (resourceId == 0) {
                Log.w(TAG, "Could not find drawable resource: " + spriteData.textureResource + ". Skipping sprite.");
                Log.w(TAG, "Tried to resolve: package=" + context.getPackageName() + ", name=" + spriteData.textureResource);
                continue;
            }

            Log.d(TAG, "Successfully resolved texture: " + spriteData.textureResource + " -> resourceId=" + resourceId);

            // Create sprite configuration
            SpriteConfig config = new SpriteConfig(
                resourceId,
                spriteData.width,
                spriteData.height,
                spriteData.parallaxMultiplier,
                spriteData.positionX,
                spriteData.positionY
            );

            // Create sprite and add to scene
            Sprite sprite = new Sprite(config);
            scene.addSprite(sprite);
        }

        Log.d(TAG, "Successfully loaded scene '" + scene.getSceneName() + "' with "
            + scene.getSprites().size() + " sprites");

        return scene;
    }

    /**
     * Get the drawable resource ID from a resource name.
     *
     * @param resourceName the name of the drawable (e.g., "knight", "tower")
     * @return the resource ID, or 0 if not found
     */
    private int getDrawableResourceId(String resourceName) {
        return context.getResources().getIdentifier(
            resourceName,
            "drawable",
            context.getPackageName()
        );
    }
}

