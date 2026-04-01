package com.example.livewallpaper.scene.managers;

import android.content.Context;
import com.example.livewallpaper.logging.TimberLog;

import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.scene.SceneLoader;
import com.example.livewallpaper.scene.ScenePicker;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.managers.SceneFileManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages scene switching logic, cycling between multiple scenes on demand.
 * Handles loading the next scene, applying gyro scaling, initiating transitions,
 * and managing the complete transition lifecycle.
 */
public class SceneSwitchManager {
    private static final String TAG = "SceneSwitchManager";

    private final Context context;
    private final SceneLoader sceneLoader;
    private final SceneTransitionManager transitionManager;
    // Dynamically loaded list of available scene files
    private String[] sceneFiles;


    // Reference to the current scene (updated externally)
    private Scene currentScene;

    // Callback for gyro scaling application
    private GyroScalingCallback gyroCallback;

    // Scene picker for time-of-day based scene selection
    private ScenePicker scenePicker;
    private List<Scene> loadedScenes;

    /**
     * Callback interface for applying gyro scaling to new scenes
     */
    public interface GyroScalingCallback {
        void applyGyroScalingToNewScene(Scene newScene);
    }

    public SceneSwitchManager(Context context, SceneFileManager sceneFileManager) {
        this.context = context;
        this.sceneLoader = new SceneLoader(context);
        this.transitionManager = new SceneTransitionManager();
        this.sceneFiles = sceneFileManager.loadAvailableSceneFiles();
        this.loadedScenes = new ArrayList<>();

        // Set the persistent scenes path on the loader
        String persistentPath = sceneFileManager.getPersistentScenesDirectoryPath();
        if (persistentPath != null) {
            sceneLoader.setPersistentScenesPath(persistentPath);
        }

        // Preload all scene files into memory
        loadAllScenes();

        // Initialize scene picker with all loaded scenes
        this.scenePicker = new ScenePicker(this.loadedScenes);
        TimberLog.d(TAG, "SceneSwitchManager initialized with " + loadedScenes.size() + " scenes");
    }

    /**
     * Load all scene files into memory upfront.
     * This ensures ScenePicker has all scenes available for selection.
     */
    private void loadAllScenes() {
        for (String sceneFile : sceneFiles) {
            try {
                Scene scene = sceneLoader.loadScene(sceneFile);
                loadedScenes.add(scene);
                TimberLog.d(TAG, "Preloaded scene: " + scene.getSceneName());
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to preload scene: " + sceneFile, e);
            }
        }
    }

    /**
     * Get the default/initial scene file to load
     * @return the filename of the first scene to display
     */
    public String getInitialSceneFile() {
        if (sceneFiles.length == 0) {
            throw new RuntimeException("No scene files found in assets/scenes");
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
                TimberLog.d(TAG, "Initialized scene manager at index " + i + " (" + fileName + ")");
                break;
            }
        }
    }


    /**
     * Set the gyro scaling callback for applying transformations to new scenes
     */
    public void setGyroScalingCallback(GyroScalingCallback callback) {
        this.gyroCallback = callback;
    }

    /**
     * Check if a scene transition is currently in progress.
     * Used to prevent interrupting an ongoing transition.
     *
     * @return true if a transition is active, false otherwise
     */
    public boolean isTransitioning() {
        return transitionManager.isTransitioning();
    }

    /**
     * Cycle to the next scene in the sequence.
     * Must be called on the GL thread.
     *
     * @param currentScene The currently active scene to transition FROM
     */
    public void cycleToNextScene(Scene currentScene) {
        if (loadedScenes.isEmpty()) {
            TimberLog.w(TAG, "No scenes available to cycle through");
            return;
        }

        // Ensure currentScene is set to what's actually being displayed
        this.currentScene = currentScene;
        Scene newScene = scenePicker.getNextScene(currentScene);

        if(newScene.getSceneName().equals(currentScene.getSceneName()))
        {
            return;
        }

        // Reset the preloaded scene so textures can be re-initialized
        newScene.resetForReuse();

        TimberLog.d(TAG, "Cycling to next scene: " + newScene.getSceneName());

        try {
            // Apply gyro scaling if callback is set and gyro is currently active
            if (gyroCallback != null) {
                gyroCallback.applyGyroScalingToNewScene(newScene);
            }

            // Start the transition with the transition manager
            // The transition manager will handle texture preloading when the new scene initializes
            transitionManager.startTransition(this.currentScene, newScene, context);

        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to start transition to new scene", e);
        }
    }

    /**
     * Update scene transition state. Call this every frame from onDrawFrame.
     * Handles texture preloading completion, alpha fading, and scene switching.
     * Must be called on the GL thread.
     *
     * @return the scene that should be rendered this frame
     */
    public Scene updateTransition(TextureManager textureManager) {
        if (!transitionManager.isTransitioning()) {
            return currentScene;
        }

        Scene sceneToRender = transitionManager.updateTransition(textureManager);

        // If transition just finished, update our reference
        if (!transitionManager.isTransitioning() && sceneToRender != currentScene) {
            currentScene = sceneToRender;
            TimberLog.d(TAG, "Scene switched successfully to: " + currentScene.getSceneName());
        }

        return sceneToRender;
    }

    public float getXFocus()
    {
        // If we're in a transition, smoothly transition to the next scene's xFocus
        // Otherwise, use the current scene's xFocus
        if (transitionManager.isTransitioning()) {
            Scene transitioningScene = transitionManager.getNewScene();
            if (transitioningScene != null) {
                return transitioningScene.getXFocus();
            }
        }
        return currentScene.getXFocus();
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

    /**
     * Reload the available scene files from disk.
     * Call this when the scene list has changed (e.g., after reset or delete operations).
     * This updates the internal sceneFiles array to reflect the current state,
     * reloads all scenes into memory, and updates the ScenePicker.
     *
     * @param sceneFileManager the SceneFileManager to load scene files from
     */
    public void reloadAvailableScenes(SceneFileManager sceneFileManager) {
        this.sceneFiles = sceneFileManager.loadAvailableSceneFiles();

        // Clear and reload all scenes into memory
        loadedScenes.clear();
        loadAllScenes();

        // Update ScenePicker with the newly loaded scenes
        this.scenePicker = new ScenePicker(this.loadedScenes);

        TimberLog.d(TAG, "Reloaded " + sceneFiles.length + " scene files and refreshed " + loadedScenes.size() + " scenes in memory");
    }
}
