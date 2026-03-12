package com.example.livewallpaper.ui.editor.managers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.example.livewallpaper.logging.TimberLog;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SceneData;
import com.example.livewallpaper.scene.managers.BaseSceneManager;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages scene file operations using persistent URI permissions.
 * Users select a folder where scenes will be stored, and the app obtains persistent
 * access to that folder using takePersistableUriPermission(). This allows the app
 * to maintain access even after uninstall/reinstall on Android 11+.
 */
public class SceneFileManager {
    private static final String TAG = "SceneFileManager";
    private static final String SCENES_FOLDER = "scenes";
    private static final String PREFS_NAME = "SceneFileManager";
    private static final String PREFS_KEY_SCENES_URI = "scenes_directory_uri";

    private final Context context;
    private final BaseSceneManager renderer;
    private final Uri scenesDirectoryUri;

    public SceneFileManager(Context context, BaseSceneManager renderer) {
        this.context = context;
        this.renderer = renderer;
        this.scenesDirectoryUri = loadScenesDirectoryUri();
    }

    /**
     * Load the saved scenes directory URI from SharedPreferences.
     *
     * @return the Uri to the scenes directory, or null if not set
     */
    private Uri loadScenesDirectoryUri() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(PREFS_KEY_SCENES_URI, null);
        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            TimberLog.d(TAG, "Loaded scenes directory URI: " + uri);
            return uri;
        }
        return null;
    }

    /**
     * Load all scene files and cache their metadata (filename and timeOfDay).
     * This is more efficient than loading individual files on-demand.
     *
     * @return a Map of filename -> timeOfDay string (e.g., "DAWN", "DAY", "SUNSET", "NIGHT")
     */
    public Map<String, String> loadSceneMetadata() {
        Map<String, String> sceneMetadata = new HashMap<>();
        Gson gson = new Gson();

        // Load from fallback directory (always available)
        File fallbackDir = getFallbackScenesDirectory();
        File[] sceneFiles = fallbackDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (sceneFiles != null) {
            for (File sceneFile : sceneFiles) {
                try (java.io.FileReader reader = new java.io.FileReader(sceneFile)) {
                    SceneData sceneData = gson.fromJson(reader, SceneData.class);
                    String timeOfDayStr = (sceneData != null && sceneData.timeOfDay != null)
                        ? sceneData.timeOfDay.toString()
                        : "DAY"; // Default to DAY if not found
                    sceneMetadata.put(sceneFile.getName(), timeOfDayStr);
                    TimberLog.d(TAG, "Loaded metadata for " + sceneFile.getName() + ": " + timeOfDayStr);
                } catch (IOException e) {
                    TimberLog.w(TAG, "Error loading metadata for scene " + sceneFile.getName() + ": " + e.getMessage());
                    // Put a default value if loading fails
                    sceneMetadata.put(sceneFile.getName(), "DAY");
                }
            }
        }

        TimberLog.d(TAG, "Loaded metadata for " + sceneMetadata.size() + " scenes");
        return sceneMetadata;
    }
    public String[] loadAvailableSceneFiles() {
        // If no directory is configured, use fallback directory and copy from bundle
        if (scenesDirectoryUri == null) {
            return loadFromFallbackDirectory();
        }

        try {
            // List files from the URI
            String[] fileNames = listJsonFilesFromUri(scenesDirectoryUri);

            if (fileNames.length == 0) {
                TimberLog.w(TAG, "No scene files found in configured directory: " + scenesDirectoryUri);
                // Fall back to app bundle if URI directory is empty
                return loadFromFallbackDirectory();
            }

            TimberLog.d(TAG, "Found " + fileNames.length + " scene files in configured directory");
            return fileNames;
        } catch (Exception e) {
            TimberLog.e(TAG, "Error loading scene files from URI: " + e.getMessage(), e);
            // Fall back to app bundle on error
            return loadFromFallbackDirectory();
        }
    }

    /**
     * Load scene files from fallback directory (app-specific external storage).
     * If the fallback directory is empty, copies all files from the app bundle.
     *
     * @return array of scene filenames
     */
    private String[] loadFromFallbackDirectory() {
        File fallbackDir = getFallbackScenesDirectory();
        ensureFallbackScenesInitialized(fallbackDir);

        File[] files = fallbackDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            TimberLog.w(TAG, "No scene files found in fallback directory: " + fallbackDir.getAbsolutePath());
            return new String[0];
        }

        java.util.Arrays.sort(files);
        List<String> fileNames = new ArrayList<>();
        for (File file : files) {
            fileNames.add(file.getName());
        }

        return fileNames.toArray(new String[0]);
    }

    /**
     * List all JSON files from a document tree URI.
     *
     * @param treeUri the document tree URI
     * @return array of filenames
     */
    private String[] listJsonFilesFromUri(Uri treeUri) {
        List<String> fileNames = new ArrayList<>();

        try {
            // Use DocumentFile API to list files
            DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);
            if (dir == null || !dir.isDirectory()) {
                TimberLog.e(TAG, "Invalid directory URI or not a directory");
                return new String[0];
            }

            for (DocumentFile file : dir.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().endsWith(".json")) {
                    fileNames.add(file.getName());
                }
            }

            // Sort alphabetically
            fileNames.sort(String::compareTo);
        } catch (Exception e) {
            TimberLog.e(TAG, "Error listing files from URI", e);
        }

        return fileNames.toArray(new String[0]);
    }

    /**
     * Get or create the fallback scenes directory in app-specific external storage.
     * Used when the user hasn't configured a persistent directory yet.
     *
     * @return the fallback scenes directory
     */
    private File getFallbackScenesDirectory() {
        File externalFilesDir = context.getExternalFilesDir("scenes");
        if (externalFilesDir == null) {
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir != null) {
                externalFilesDir = new File(cacheDir.getParent(), "files/scenes");
            } else {
                externalFilesDir = new File(context.getCacheDir(), "scenes");
            }
        }

        if (!externalFilesDir.exists()) {
            boolean created = externalFilesDir.mkdirs();
            if (created) {
                TimberLog.d(TAG, "Created fallback scenes directory: " + externalFilesDir.getAbsolutePath());
            } else {
                TimberLog.w(TAG, "Failed to create fallback scenes directory");
            }
        }

        return externalFilesDir;
    }


    /**
     * Ensure the fallback scenes directory has at least one scene file.
     * If empty, copies all scene files from the app bundle.
     */
    private void ensureFallbackScenesInitialized(File fallbackDir) {
        File[] files = fallbackDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (files != null && files.length > 0) {
            // Fallback folder already has scene files
            return;
        }

        TimberLog.d(TAG, "Fallback scenes folder is empty, copying from app bundle...");

        try {
            String[] bundleScenes = context.getAssets().list(SCENES_FOLDER);
            if (bundleScenes == null || bundleScenes.length == 0) {
                TimberLog.e(TAG, "No scene files found in app bundle");
                return;
            }

            for (String sceneName : bundleScenes) {
                if (sceneName.endsWith(".json")) {
                    copySceneFromBundleToDir(sceneName, fallbackDir);
                }
            }

            TimberLog.d(TAG, "Successfully copied " + bundleScenes.length + " scene files from app bundle");
        } catch (IOException e) {
            TimberLog.e(TAG, "Failed to copy scene files from app bundle", e);
        }
    }

    /**
     * Copy a scene file from the app bundle to a specified directory.
     *
     * @param sceneName the name of the scene file to copy
     * @param targetDir the directory to copy to
     * @throws IOException if copying fails
     */
    private void copySceneFromBundleToDir(String sceneName, File targetDir) throws IOException {
        try (InputStream inputStream = context.getAssets().open(SCENES_FOLDER + "/" + sceneName)) {
            File targetFile = new File(targetDir, sceneName);

            // Delete existing file to avoid permission conflicts
            if (targetFile.exists()) {
                boolean deleted = targetFile.delete();
                if (!deleted) {
                    TimberLog.w(TAG, "Failed to delete existing file before copying: " + sceneName);
                }
            }

            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            TimberLog.d(TAG, "Copied scene file from bundle: " + sceneName);
        }
    }

    /**
     * Get the path to a scene file. If a URI is configured, returns null (use URI instead).
     * Otherwise, returns the path in the fallback directory.
     *
     * @param sceneName the name of the scene file
     * @return the absolute path to the scene file, or null if using URI-based access
     */
    public String getSceneFilePath(String sceneName) {
        if (scenesDirectoryUri != null) {
            // When using URI, the path is not directly accessible
            TimberLog.d(TAG, "Using URI-based access for: " + sceneName);
            return null;
        }
        // Fallback to file system path
        return new File(getFallbackScenesDirectory(), sceneName).getAbsolutePath();
    }

    /**
     * Get the path to the fallback scenes directory.
     *
     * @return the absolute path to the fallback scenes directory
     */
    public String getFallbackScenesDirectoryPath() {
        return getFallbackScenesDirectory().getAbsolutePath();
    }

    /**
     * Get the path to the persistent scenes directory.
     * This method is kept for backward compatibility with existing code.
     * It returns the fallback directory path when no custom URI is configured,
     * or the fallback directory when in fallback mode.
     *
     * @return the absolute path to the persistent scenes directory
     * @deprecated Use {@link #getFallbackScenesDirectoryPath()} for fallback mode
     */
    @Deprecated
    public String getPersistentScenesDirectoryPath() {
        return getFallbackScenesDirectoryPath();
    }

    /**
     * Delete a scene file. Works with both URI-based and fallback directory access.
     *
     * @param sceneName the name of the scene file to delete
     * @return true if the deletion was successful, false otherwise
     */
    public boolean deleteScene(String sceneName) {
        try {
            if (scenesDirectoryUri != null) {
                // Delete from URI-based directory
                return deleteSceneFromUri(sceneName);
            } else {
                // Delete from fallback directory
                File sceneFile = new File(getFallbackScenesDirectory(), sceneName);

                if (!sceneFile.exists()) {
                    TimberLog.w(TAG, "Scene file does not exist: " + sceneName);
                    return false;
                }

                boolean deleted = sceneFile.delete();
                if (deleted) {
                    TimberLog.d(TAG, "Successfully deleted scene file: " + sceneName);
                } else {
                    TimberLog.e(TAG, "Failed to delete scene file: " + sceneName);
                }

                return deleted;
            }
        } catch (Exception e) {
            TimberLog.e(TAG, "Error deleting scene file: " + sceneName, e);
            return false;
        }
    }

    /**
     * Delete a scene file from a URI-based directory.
     *
     * @param sceneName the name of the scene file to delete
     * @return true if the deletion was successful, false otherwise
     */
    private boolean deleteSceneFromUri(String sceneName) {
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(context, scenesDirectoryUri);
            if (dir == null || !dir.isDirectory()) {
                TimberLog.e(TAG, "Invalid directory URI");
                return false;
            }

            for (DocumentFile file : dir.listFiles()) {
                if (file.isFile() && sceneName.equals(file.getName())) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        TimberLog.d(TAG, "Successfully deleted scene file from URI: " + sceneName);
                    } else {
                        TimberLog.e(TAG, "Failed to delete scene file from URI: " + sceneName);
                    }
                    return deleted;
                }
            }

            TimberLog.w(TAG, "Scene file not found in URI directory: " + sceneName);
            return false;
        } catch (Exception e) {
            TimberLog.e(TAG, "Error deleting scene file from URI: " + sceneName, e);
            return false;
        }
    }

    /**
     * Reset the scene list to default by clearing persistent storage and copying
     * all default scene files from the app bundle.
     */
    public void resetToDefaultScenes() {
        try {
            if (scenesDirectoryUri != null) {
                // Reset URI-based directory
                resetScenesInUri();
            } else {
                // Reset fallback directory
                resetScenesInFallback();
            }
        } catch (IOException e) {
            TimberLog.e(TAG, "Error resetting scenes to default", e);
            throw new RuntimeException("Failed to reset scenes", e);
        }
    }

    /**
     * Reset scenes in the URI-based directory.
     */
    private void resetScenesInUri() throws IOException {
        DocumentFile dir = DocumentFile.fromTreeUri(context, scenesDirectoryUri);
        if (dir == null || !dir.isDirectory()) {
            TimberLog.e(TAG, "Invalid directory URI");
            return;
        }

        // Delete all JSON files
        for (DocumentFile file : dir.listFiles()) {
            if (file.isFile() && file.getName() != null && file.getName().endsWith(".json")) {
                if (file.delete()) {
                    TimberLog.d(TAG, "Deleted scene file during reset: " + file.getName());
                }
            }
        }

        // Copy all default scene files from app bundle
        String[] bundleScenes = context.getAssets().list(SCENES_FOLDER);
        if (bundleScenes != null) {
            for (String sceneName : bundleScenes) {
                if (sceneName.endsWith(".json")) {
                    copySceneFromBundleToUri(sceneName);
                }
            }
            TimberLog.d(TAG, "Successfully reset scene list in URI to " + bundleScenes.length + " default scenes");
        }
    }

    /**
     * Reset scenes in the fallback directory.
     */
    private void resetScenesInFallback() throws IOException {
        File fallbackDir = getFallbackScenesDirectory();

        // Delete all files in the fallback directory
        File[] files = fallbackDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.delete()) {
                    TimberLog.d(TAG, "Deleted scene file during reset: " + file.getName());
                }
            }
        }

        // Copy all default scene files from app bundle
        String[] bundleScenes = context.getAssets().list(SCENES_FOLDER);
        if (bundleScenes != null) {
            for (String sceneName : bundleScenes) {
                if (sceneName.endsWith(".json")) {
                    copySceneFromBundleToDir(sceneName, fallbackDir);
                }
            }
            TimberLog.d(TAG, "Successfully reset scene list to " + bundleScenes.length + " default scenes");
        }
    }

    /**
     * Copy a scene file from the app bundle to a URI-based directory.
     *
     * @param sceneName the name of the scene file to copy
     * @throws IOException if copying fails
     */
    private void copySceneFromBundleToUri(String sceneName) throws IOException {
        try (InputStream inputStream = context.getAssets().open(SCENES_FOLDER + "/" + sceneName)) {
            DocumentFile dir = DocumentFile.fromTreeUri(context, scenesDirectoryUri);
            if (dir == null || !dir.isDirectory()) {
                TimberLog.e(TAG, "Invalid directory URI for copying scene");
                return;
            }

            // Create or overwrite file in the URI directory
            DocumentFile newFile = dir.createFile("application/json", sceneName);
            if (newFile == null) {
                // Try to find and delete existing file first
                for (DocumentFile file : dir.listFiles()) {
                    if (sceneName.equals(file.getName())) {
                        file.delete();
                        break;
                    }
                }
                // Try creating again
                newFile = dir.createFile("application/json", sceneName);
            }

            if (newFile != null) {
                try (OutputStream outputStream = context.getContentResolver().openOutputStream(newFile.getUri())) {
                    if (outputStream != null) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        TimberLog.d(TAG, "Copied scene file from bundle to URI: " + sceneName);
                    }
                }
            } else {
                TimberLog.e(TAG, "Failed to create file in URI directory: " + sceneName);
            }
        }
    }

    /**
     * Show the save scene dialog.
     *
     * @param currentSceneName the current scene name to use as default
     * @param onSuccess callback when save is successful
     */
    public void showSaveDialog(String currentSceneName, Runnable onSuccess) {
        // If URI is configured, we need to use the document provider to save
        if (scenesDirectoryUri != null) {
            showSaveDialogWithUri(currentSceneName, onSuccess);
        } else {
            showSaveDialogWithFallback(currentSceneName, onSuccess);
        }
    }

    /**
     * Show save dialog for URI-based storage.
     */
    private void showSaveDialogWithUri(String currentSceneName, Runnable onSuccess) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Save Scene");

        final android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        if (currentSceneName != null) {
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

                saveSceneToUri(sceneName);
                Toast.makeText(context, "Scene saved to selected folder", Toast.LENGTH_SHORT).show();

                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                TimberLog.e(TAG, "Error saving scene: " + e.getMessage(), e);
                Toast.makeText(context, "Error saving scene: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Show save dialog for fallback directory storage.
     */
    private void showSaveDialogWithFallback(String currentSceneName, Runnable onSuccess) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Save Scene");

        final android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        if (currentSceneName != null) {
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
                Toast.makeText(context, "Scene saved to storage", Toast.LENGTH_SHORT).show();

                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                TimberLog.e(TAG, "Error saving scene: " + e.getMessage(), e);
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
        sceneData.timeOfDay = scene.getTimeOfDay();

        // Create SpriteData array from current sprites
        List<SpriteData> spriteDatas = new ArrayList<>();
        for (Sprite sprite : scene.getSprites()) {
            SpriteData spriteData = new SpriteData();
            spriteData.name = sprite.getName();
            spriteData.textureResource = sprite.getTextureResource();
            // Use original width/height to avoid saving gyro-scaled values
            spriteData.width = sprite.getOriginalWidth();
            spriteData.height = sprite.getOriginalHeight();
            // Use original position to avoid saving gyro-scaled positions
            spriteData.positionX = sprite.getOriginalPositionX();
            spriteData.positionY = sprite.getOriginalPositionY();
            spriteData.parallaxMultiplier = sprite.getParallaxMultiplier();
            spriteData.texCoordinates = sprite.getTextureCoordinates();

            // Texture coordinates are now the ONLY texture state stored
            // Scale/offset are derived from these coordinates if needed for UI display
            TimberLog.d(TAG, "Saving texture coordinates for sprite: " + sprite.getName());

            spriteDatas.add(spriteData);
        }
        sceneData.sprites = spriteDatas.toArray(new SpriteData[0]);

        // Serialize to JSON with pretty printing for readability
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        String sceneJson = gson.toJson(sceneData);

        // Create file in fallback directory with .json extension
        String fileName = sceneName.endsWith(".json") ? sceneName : sceneName + ".json";
        File fallbackDir = getFallbackScenesDirectory();
        File sceneFile = new File(fallbackDir, fileName);

        // Write to file
        try (FileWriter writer = new FileWriter(sceneFile)) {
            writer.write(sceneJson);
        }

        TimberLog.d(TAG, "Scene saved to fallback storage: " + sceneFile.getAbsolutePath());
    }

    /**
     * Save the scene to a URI-based directory.
     *
     * @param sceneName the name for the scene file
     * @throws Exception if saving fails
     */
    private void saveSceneToUri(String sceneName) throws Exception {
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
        sceneData.timeOfDay = scene.getTimeOfDay();

        // Create SpriteData array from current sprites
        List<SpriteData> spriteDatas = new ArrayList<>();
        for (Sprite sprite : scene.getSprites()) {
            SpriteData spriteData = new SpriteData();
            spriteData.name = sprite.getName();
            spriteData.textureResource = sprite.getTextureResource();
            spriteData.width = sprite.getOriginalWidth();
            spriteData.height = sprite.getOriginalHeight();
            spriteData.positionX = sprite.getOriginalPositionX();
            spriteData.positionY = sprite.getOriginalPositionY();
            spriteData.parallaxMultiplier = sprite.getParallaxMultiplier();
            spriteData.texCoordinates = sprite.getTextureCoordinates();

            // Texture coordinates are now the ONLY texture state stored
            // Scale/offset are derived from these coordinates if needed for UI display
            TimberLog.d(TAG, "Saving texture coordinates for sprite: " + sprite.getName());

            spriteDatas.add(spriteData);
        }
        sceneData.sprites = spriteDatas.toArray(new SpriteData[0]);

        // Serialize to JSON with pretty printing for readability
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        String sceneJson = gson.toJson(sceneData);

        // Write to URI-based directory
        String fileName = sceneName.endsWith(".json") ? sceneName : sceneName + ".json";

        DocumentFile dir = DocumentFile.fromTreeUri(context, scenesDirectoryUri);
        if (dir == null || !dir.isDirectory()) {
            throw new Exception("Invalid scenes directory URI");
        }

        // Try to find existing file
        DocumentFile existingFile = null;
        for (DocumentFile file : dir.listFiles()) {
            if (file.isFile() && fileName.equals(file.getName())) {
                existingFile = file;
                break;
            }
        }

        // Create or use existing file
        DocumentFile targetFile = existingFile;
        if (targetFile == null) {
            targetFile = dir.createFile("application/json", fileName);
        }

        if (targetFile == null) {
            throw new Exception("Failed to create scene file in URI directory");
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(targetFile.getUri())) {
            if (outputStream == null) {
                throw new Exception("Failed to open output stream for scene file");
            }
            outputStream.write(sceneJson.getBytes());
        }

        TimberLog.d(TAG, "Scene saved to URI directory: " + fileName);
    }
}
