package com.example.livewallpaper.ui.managers;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.livewallpaper.scene.Scene;
import com.example.livewallpaper.scene.SceneData;
import com.example.livewallpaper.scene.SceneManager;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.scene.SpriteData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages scene file operations including saving, loading, and dialogs.
 * Scene files are stored in persistent storage (Documents folder) and persist across app uninstalls.
 */
public class SceneFileManager {
    private static final String TAG = "SceneFileManager";
    private static final String SCENES_FOLDER = "scenes";
    private static final String PERSISTENT_SCENES_FOLDER = "LiveWallpaperScenes";

    private final Context context;
    private final SceneManager renderer;
    private File persistentScenesDir;

    public SceneFileManager(Context context, SceneManager renderer) {
        this.context = context;
        this.renderer = renderer;
        this.persistentScenesDir = getPersistentScenesDirectory();
    }

    /**
     * Get or create the persistent scenes directory in Documents.
     * This folder persists even if the app is uninstalled.
     *
     * @return the persistent scenes directory
     */
    private File getPersistentScenesDirectory() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        );
        File scenesDir = new File(documentsDir, PERSISTENT_SCENES_FOLDER);

        if (!scenesDir.exists()) {
            boolean created = scenesDir.mkdirs();
            if (created) {
                Log.d(TAG, "Created persistent scenes directory: " + scenesDir.getAbsolutePath());
            } else {
                Log.w(TAG, "Failed to create persistent scenes directory");
            }
        }

        return scenesDir;
    }

    /**
     * Load all available .json files from the persistent scenes folder.
     * If the folder is empty, copies all scene files from the app bundle.
     *
     * @return array of scene filenames, sorted alphabetically
     */
    public String[] loadAvailableSceneFiles() {
        // First, ensure the persistent directory has scene files
        ensurePersistentScenesInitialized();

        // Load from persistent storage
        File[] files = persistentScenesDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            Log.w(TAG, "No scene files found in persistent storage: " + persistentScenesDir.getAbsolutePath());
            return new String[0];
        }

        // Sort alphabetically for consistent ordering
        java.util.Arrays.sort(files);

        List<String> fileNames = new ArrayList<>();
        for (File file : files) {
            fileNames.add(file.getName());
        }

        Log.d(TAG, "Found " + fileNames.size() + " scene files in persistent storage: " + fileNames);
        return fileNames.toArray(new String[0]);
    }

    /**
     * Ensure the persistent scenes directory has at least one scene file.
     * If empty, copies all scene files from the app bundle.
     */
    private void ensurePersistentScenesInitialized() {
        File[] files = persistentScenesDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files != null && files.length > 0) {
            // Persistent folder already has scene files
            return;
        }

        Log.d(TAG, "Persistent scenes folder is empty, copying from app bundle...");

        try {
            String[] bundleScenes = context.getAssets().list(SCENES_FOLDER);
            if (bundleScenes == null || bundleScenes.length == 0) {
                Log.e(TAG, "No scene files found in app bundle");
                return;
            }

            for (String sceneName : bundleScenes) {
                if (sceneName.endsWith(".json")) {
                    copySceneFromBundle(sceneName);
                }
            }

            Log.d(TAG, "Successfully copied " + bundleScenes.length + " scene files from app bundle");
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy scene files from app bundle", e);
        }
    }

    /**
     * Copy a scene file from the app bundle to persistent storage.
     *
     * @param sceneName the name of the scene file to copy
     * @throws IOException if copying fails
     */
    private void copySceneFromBundle(String sceneName) throws IOException {
        try (InputStream inputStream = context.getAssets().open(SCENES_FOLDER + "/" + sceneName)) {
            File targetFile = new File(persistentScenesDir, sceneName);

            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            Log.d(TAG, "Copied scene file from bundle: " + sceneName);
        }
    }

    /**
     * Get the path to a scene file in persistent storage.
     *
     * @param sceneName the name of the scene file
     * @return the absolute path to the scene file
     */
    public String getSceneFilePath(String sceneName) {
        return new File(persistentScenesDir, sceneName).getAbsolutePath();
    }

    /**
     * Get the path to the persistent scenes directory.
     *
     * @return the absolute path to the persistent scenes directory
     */
    public String getPersistentScenesDirectoryPath() {
        return persistentScenesDir.getAbsolutePath();
    }

    /**
     * Delete a scene file from persistent storage.
     *
     * @param sceneName the name of the scene file to delete
     * @return true if the deletion was successful, false otherwise
     */
    public boolean deleteScene(String sceneName) {
        try {
            File sceneFile = new File(persistentScenesDir, sceneName);

            if (!sceneFile.exists()) {
                Log.w(TAG, "Scene file does not exist: " + sceneName);
                return false;
            }

            boolean deleted = sceneFile.delete();
            if (deleted) {
                Log.d(TAG, "Successfully deleted scene file: " + sceneName);
            } else {
                Log.e(TAG, "Failed to delete scene file: " + sceneName);
            }

            return deleted;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting scene file: " + sceneName, e);
            return false;
        }
    }

    /**
     * Reset the scene list to default by clearing persistent storage and copying
     * all default scene files from the app bundle.
     */
    public void resetToDefaultScenes() {
        try {
            // Delete all files in the persistent scenes directory
            File[] files = persistentScenesDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.delete()) {
                        Log.d(TAG, "Deleted scene file during reset: " + file.getName());
                    }
                }
            }

            // Copy all default scene files from app bundle
            String[] bundleScenes = context.getAssets().list(SCENES_FOLDER);
            if (bundleScenes != null) {
                for (String sceneName : bundleScenes) {
                    if (sceneName.endsWith(".json")) {
                        copySceneFromBundle(sceneName);
                    }
                }
                Log.d(TAG, "Successfully reset scene list to " + bundleScenes.length + " default scenes");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error resetting scenes to default", e);
            throw new RuntimeException("Failed to reset scenes", e);
        }
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
                Toast.makeText(context, "Scene saved to persistent storage", Toast.LENGTH_SHORT).show();

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
     * Save the scene to persistent storage.
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
            // Use original width/height to avoid saving gyro-scaled values
            spriteData.width = sprite.getOriginalWidth();
            spriteData.height = sprite.getOriginalHeight();
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

        // Create file in persistent storage with .json extension
        String fileName = sceneName.endsWith(".json") ? sceneName : sceneName + ".json";
        File sceneFile = new File(persistentScenesDir, fileName);

        // Write to file
        try (FileWriter writer = new FileWriter(sceneFile)) {
            writer.write(sceneJson);
        }

        Log.d(TAG, "Scene saved to persistent storage: " + sceneFile.getAbsolutePath());
    }
}
