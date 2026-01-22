package com.example.livewallpaper.scene;

import android.content.Context;
import android.util.Log;

import com.example.livewallpaper.gl.TextureManager;

/**
 * Manages scene switching logic, cycling between multiple scenes on demand.
 * Handles loading the next scene, applying gyro scaling, initiating transitions,
 * and managing the complete transition lifecycle.
 */
public class SceneManager {
    private static final String TAG = "SceneManager";
    private static final String SCENES_FOLDER = "scenes";

    private final Context context;
    private final SceneLoader sceneLoader;
    private final TextureManager textureManager;
    private final SceneTransitionManager transitionManager;

    // Dynamically loaded list of available scene files
    private String[] sceneFiles;

    // Current index in the sceneFiles array
    private int currentSceneIndex = 0;

    // Reference to the current scene (updated externally)
    private Scene currentScene;

    // Callback for gyro scaling application
    private GyroScalingCallback gyroCallback;

    /**
     * Callback interface for applying gyro scaling to new scenes
     */
    public interface GyroScalingCallback {
        void applyGyroScalingToNewScene(Scene newScene, float worldHeight);
    }

    public SceneManager(Context context) {
        this.context = context;
        this.sceneLoader = new SceneLoader(context);
        this.textureManager = new TextureManager();
        this.transitionManager = new SceneTransitionManager(textureManager);
        this.sceneFiles = loadAvailableSceneFiles();
    }

    /**
     * Load all available .json files from the scenes folder in assets.
     *
     * @return array of scene filenames, sorted alphabetically
     */
    private String[] loadAvailableSceneFiles() {
        try {
            String[] files = context.getAssets().list(SCENES_FOLDER);
            if (files == null || files.length == 0) {
                Log.w(TAG, "No scene files found in assets/" + SCENES_FOLDER);
                return new String[0];
            }

            // Filter for .json files and sort
            java.util.List<String> jsonFiles = new java.util.ArrayList<>();
            for (String file : files) {
                if (file.endsWith(".json")) {
                    jsonFiles.add(file);
                }
            }

            if (jsonFiles.isEmpty()) {
                Log.w(TAG, "No .json files found in assets/" + SCENES_FOLDER);
                return new String[0];
            }

            // Sort alphabetically for consistent ordering
            java.util.Collections.sort(jsonFiles);

            Log.d(TAG, "Found " + jsonFiles.size() + " scene files: " + jsonFiles);
            return jsonFiles.toArray(new String[0]);

        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to load scene files from assets", e);
            return new String[0];
        }
    }

    /**
     * Get the default/initial scene file to load
     * @return the filename of the first scene to display
     */
    public String getInitialSceneFile() {
        if (sceneFiles.length == 0) {
            throw new RuntimeException("No scene files found in assets/" + SCENES_FOLDER);
        }
        return sceneFiles[0];
    }

    /**
     * Load the initial scene from the configured initial scene file.
     *
     * @return the loaded Scene
     * @throws Exception if scene loading fails
     */
    public Scene loadInitialScene() throws Exception {
        String initialSceneFile = getInitialSceneFile();
        return sceneLoader.loadScene(initialSceneFile);
    }

    /**
     * Initialize the scene manager with the first scene
     */
    public void initialize(Scene initialScene) {
        this.currentScene = initialScene;
        // Find the index of the current scene in the dynamically loaded list
        String sceneName = initialScene.getSceneName();
        for (int i = 0; i < sceneFiles.length; i++) {
            String fileName = sceneFiles[i];
            String fileNameWithoutExtension = fileName.endsWith(".json") ?
                fileName.substring(0, fileName.length() - 5) : fileName;

            if (fileNameWithoutExtension.equals(sceneName)) {
                currentSceneIndex = i;
                Log.d(TAG, "Initialized scene manager at index " + i + " (" + fileName + ")");
                break;
            }
        }
    }

    /**
     * Get the texture manager owned by this scene manager.
     * Used by the renderer to initialize scenes and handle texture loading.
     */
    public TextureManager getTextureManager() {
        return textureManager;
    }

    /**
     * Set the gyro scaling callback for applying transformations to new scenes
     */
    public void setGyroScalingCallback(GyroScalingCallback callback) {
        this.gyroCallback = callback;
    }


    /**
     * Cycle to the next scene in the sequence.
     * Must be called on the GL thread.
     *
     * @param currentScene The currently active scene to transition FROM
     * @param worldHeight The world height for gyro scaling calculations
     */
    public void cycleToNextScene(Scene currentScene, float worldHeight) {
        if (sceneFiles.length == 0) {
            Log.w(TAG, "No scene files available to cycle through");
            return;
        }

        // Ensure currentScene is set to what's actually being displayed
        this.currentScene = currentScene;

        // Update index to match the current scene being displayed
        String sceneName = currentScene.getSceneName();
        for (int i = 0; i < sceneFiles.length; i++) {
            String fileName = sceneFiles[i];
            String fileNameWithoutExtension = fileName.endsWith(".json") ?
                fileName.substring(0, fileName.length() - 5) : fileName;

            if (fileNameWithoutExtension.equals(sceneName)) {
                currentSceneIndex = i;
                break;
            }
        }

        // Move to next scene index, wrapping around
        currentSceneIndex = (currentSceneIndex + 1) % sceneFiles.length;
        String nextSceneFile = sceneFiles[currentSceneIndex];

        Log.d(TAG, "Cycling to next scene: " + nextSceneFile + " (index " + currentSceneIndex + ")");

        try {
            // Load the next scene WITHOUT initializing textures yet
            Scene newScene = sceneLoader.loadScene(nextSceneFile);
            Log.d(TAG, "Loaded new scene: " + newScene.getSceneName() + " (textures not yet initialized)");

            // Apply gyro scaling if callback is set and gyro is currently active
            if (gyroCallback != null) {
                gyroCallback.applyGyroScalingToNewScene(newScene, worldHeight);
            }

            // Start the transition with the transition manager
            // The transition manager will handle texture preloading when the new scene initializes
            transitionManager.startTransition(this.currentScene, newScene, context);

        } catch (Exception e) {
            Log.e(TAG, "Failed to load new scene for transition", e);
        }
    }

    /**
     * Update scene transition state. Call this every frame from onDrawFrame.
     * Handles texture preloading completion, alpha fading, and scene switching.
     * Must be called on the GL thread.
     *
     * @return the scene that should be rendered this frame
     */
    public Scene updateTransition() {
        if (!transitionManager.isTransitioning()) {
            return currentScene;
        }

        Scene sceneToRender = transitionManager.updateTransition();

        // If transition just finished, update our reference
        if (!transitionManager.isTransitioning() && sceneToRender != currentScene) {
            currentScene = sceneToRender;
            Log.d(TAG, "Scene switched successfully to: " + currentScene.getSceneName());
        }

        return sceneToRender;
    }

    /**
     * Get the next scene that is currently being transitioned to, if one exists.
     * Returns null if no transition is in progress.
     *
     * @return the new scene in the transition, or null if not transitioning
     */
    public Scene getTransitioningScene() {
        return transitionManager.getNewScene();
    }
}

