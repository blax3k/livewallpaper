package com.example.livewallpaper.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.livewallpaper.scene.Scene;
import com.example.livewallpaper.scene.SceneData;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.scene.SpriteData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages scene file operations including saving and dialogs.
 */
public class SceneFileManager {
    private static final String TAG = "SceneFileManager";

    private final Context context;
    private final ScenePreviewRenderer renderer;

    public SceneFileManager(Context context, ScenePreviewRenderer renderer) {
        this.context = context;
        this.renderer = renderer;
    }

    /**
     * Show the save scene dialog.
     *
     * @param currentSceneName the current scene name to use as default
     * @param onSuccess callback when save is successful
     */
    public void showSaveDialog(String currentSceneName, Runnable onSuccess) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Save Scene");

        final android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        if (currentSceneName != null) {
            // Remove .json extension if present
            if (currentSceneName.endsWith(".json")) {
                currentSceneName = currentSceneName.substring(0, currentSceneName.length() - 5);
            }
            input.setText(currentSceneName);
        }
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                String sceneName = input.getText().toString().trim();
                if (sceneName.isEmpty()) {
                    Toast.makeText(context, "Scene name cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                saveScene(sceneName);
                Toast.makeText(context, "Scene saved to Downloads", Toast.LENGTH_SHORT).show();

                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving scene: " + e.getMessage(), e);
                Toast.makeText(context, "Error saving scene: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Save the scene to the Downloads folder.
     *
     * @param sceneName the name for the scene file
     * @throws Exception if saving fails
     */
    public void saveScene(String sceneName) throws Exception {
        if (renderer == null) {
            throw new Exception("Renderer not initialized");
        }

        // Get the current scene
        Scene scene = renderer.getCurrentScene();
        if (scene == null) {
            throw new Exception("No scene loaded");
        }

        // Create SceneData object with current sprite values
        SceneData sceneData = new SceneData();
        sceneData.xFocus = scene.getXFocus();

        // Create SpriteData array from current sprites
        List<SpriteData> spriteDatas = new ArrayList<>();
        for (Sprite sprite : scene.getSprites()) {
            SpriteData spriteData = new SpriteData();
            spriteData.name = sprite.getName();
            spriteData.textureResource = sprite.getTextureResource();
            spriteData.width = sprite.getWidth();
            spriteData.height = sprite.getHeight();
            spriteData.positionX = sprite.getPositionX();
            spriteData.positionY = sprite.getPositionY();
            spriteData.parallaxMultiplier = sprite.getParallaxMultiplier();
            spriteData.texCoordinates = sprite.getTextureCoordinates();
            spriteDatas.add(spriteData);
        }
        sceneData.sprites = spriteDatas.toArray(new SpriteData[0]);

        // Serialize to JSON with pretty printing for readability
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        String sceneJson = gson.toJson(sceneData);

        // Get Downloads folder
        File downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        );

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        // Create file with .json extension
        String fileName = sceneName.endsWith(".json") ? sceneName : sceneName + ".json";
        File sceneFile = new File(downloadsDir, fileName);

        // Write to file
        try (FileWriter writer = new FileWriter(sceneFile)) {
            writer.write(sceneJson);
        }

        Log.d(TAG, "Scene saved to: " + sceneFile.getAbsolutePath());
    }
}
