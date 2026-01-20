package com.example.livewallpaper.scene;

import android.content.Context;
import android.util.Log;

import com.example.livewallpaper.gl.TextureManager;
import java.util.Set;

/**
 * Manages scene switching logic, cycling between multiple scenes on demand.
 * Handles loading the next scene, applying gyro scaling, initiating transitions,
 * and managing the complete transition lifecycle.
 */
public class SceneManager {
    private static final String TAG = "SceneManager";

    // Array of available scenes to cycle through
    private static final String[] SCENE_FILES = {
            "girl_eating.json",
            "girl_sleeping.json",
            "girl_back.json",
            "girl_leaning.json",
            "girl_smoking.json",
    };

    private Context context;
    private SceneLoader sceneLoader;
    private SceneTransitionManager transitionManager;
    private TextureManager textureManager;

    // Current index in the SCENE_FILES array
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

    public SceneManager(Context context, SceneLoader sceneLoader, TextureManager textureManager) {
        this.context = context;
        this.sceneLoader = sceneLoader;
        this.textureManager = textureManager;
        this.transitionManager = new SceneTransitionManager();
    }

    /**
     * Get the default/initial scene file to load
     * @return the filename of the first scene to display
     */
    public String getInitialSceneFile() {
        return SCENE_FILES[0];
    }

    /**
     * Initialize the scene manager with the first scene
     */
    public void initialize(Scene initialScene) {
        this.currentScene = initialScene;
        // Find the index of the current scene
        String sceneName = initialScene.getSceneName();
        for (int i = 0; i < SCENE_FILES.length; i++) {
            if (SCENE_FILES[i].equals(sceneName) || sceneName.contains(SCENE_FILES[i])) {
                currentSceneIndex = i;
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
     * Cycle to the next scene in the sequence.
     * Must be called on the GL thread.
     *
     * @param currentScene The currently active scene to transition FROM
     * @param worldHeight The world height for gyro scaling calculations
     */
    public void cycleToNextScene(Scene currentScene, float worldHeight) {
        // Ensure currentScene is set to what's actually being displayed
        this.currentScene = currentScene;

        // Update index to match the current scene being displayed
        String sceneName = currentScene.getSceneName();
        for (int i = 0; i < SCENE_FILES.length; i++) {
            if (SCENE_FILES[i].equals(sceneName) || sceneName.contains(SCENE_FILES[i])) {
                currentSceneIndex = i;
                break;
            }
        }

        // Move to next scene index, wrapping around
        currentSceneIndex = (currentSceneIndex + 1) % SCENE_FILES.length;
        String nextSceneFile = SCENE_FILES[currentSceneIndex];

        Log.d(TAG, "Cycling to next scene: " + nextSceneFile + " (from index " + ((currentSceneIndex - 1 + SCENE_FILES.length) % SCENE_FILES.length) + ")");

        try {
            // Load the next scene WITHOUT initializing textures yet
            Scene newScene = sceneLoader.loadScene(nextSceneFile);
            Log.d(TAG, "Loaded new scene: " + newScene.getSceneName() + " (textures not yet initialized)");

            // Apply gyro scaling if callback is set and gyro is currently active
            if (gyroCallback != null) {
                gyroCallback.applyGyroScalingToNewScene(newScene, worldHeight);
            }

            // Start the transition with the transition manager
            transitionManager.startTransition(this.currentScene, newScene);

            // Preload textures for the new scene on the GL thread
            if (textureManager != null) {
                newScene.initialize(context, textureManager);
                Log.d(TAG, "Textures initialized for new scene: " + newScene.getSceneName());

                // Set all sprites in the new scene to alpha=0 so they start invisible
                // The transition manager will fade them in over the FADE_DURATION_MS period
                for (Sprite sprite : newScene.getSprites()) {
                    sprite.setAlpha(0.0f);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load new scene for transition", e);
        }
    }

    /**
     * Update scene transition state. Call this every frame from onDrawFrame.
     * Handles texture preloading completion, alpha fading, and scene switching.
     *
     * Must be called on the GL thread.
     *
     * @return the scene that should be rendered this frame
     */
    public Scene updateTransition() {
        if (!transitionManager.isTransitioning()) {
            return currentScene;
        }

        Scene nextScene = transitionManager.updateTransition(currentScene);

        // Check if transition just completed
        if (!transitionManager.isTransitioning() && nextScene != currentScene) {
            // Transition is complete, switch to the new scene
            Set<Integer> oldSceneTextureIds = currentScene.getUsedTextureResourceIds();
            currentScene = nextScene;

            // Clean up textures from old scene
            Set<Integer> newSceneTextureIds = currentScene.getUsedTextureResourceIds();
            if (textureManager != null) {
                textureManager.unloadUnusedTextures(oldSceneTextureIds, newSceneTextureIds);
            }
            Log.d(TAG, "Scene switched successfully to: " + currentScene.getSceneName());
        }

        return currentScene;
    }

    /**
     * Get all sprites that should be rendered during a transition.
     * Returns sprites from both old and new scenes if transitioning, otherwise empty list.
     *
     * @return list of sprites to render for transition overlays
     */
    public java.util.List<Sprite> getTransitionSprites() {
        java.util.List<Sprite> sprites = new java.util.ArrayList<>();

        if (!transitionManager.isTransitioning()) {
            return sprites;
        }

        // Add sprites from new scene during transition
        if (transitionManager.getNewScene() != null) {
            sprites.addAll(transitionManager.getNewScene().getSprites());
        }

        return sprites;
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

