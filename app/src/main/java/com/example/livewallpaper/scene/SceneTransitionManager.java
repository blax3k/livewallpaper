package com.example.livewallpaper.scene;

import android.util.Log;

/**
 * Manages smooth scene transitions with texture preloading and crossfade effects.
 * Handles the lifecycle of transitioning from one scene to another while:
 * 1. Preloading textures for the new scene in the background
 * 2. Fading out the old scene's alpha
 * 3. Fading in the new scene's alpha once textures are ready
 */
public class SceneTransitionManager {
    private static final String TAG = "SceneTransitionManager";
    private static final long FADE_DURATION_MS = 500; // Duration of the crossfade effect

    /**
     * States of the transition process
     */
    private enum TransitionState {
        IDLE,                    // No transition in progress
        PRELOADING_TEXTURES,     // Waiting for textures to load on GL thread
        FADING,                  // Performing crossfade animation
        COMPLETE                 // Transition complete
    }

    private TransitionState state = TransitionState.IDLE;
    private Scene oldScene;
    private Scene newScene;
    private long fadeStartTimeMs = 0;

    /**
     * Check if a scene transition is currently in progress.
     *
     * @return true if transitioning, false otherwise
     */
    public boolean isTransitioning() {
        return state != TransitionState.IDLE;
    }


    /**
     * Start a new scene transition. Call this when you want to switch scenes.
     * The old scene will continue to be rendered while textures load.
     *
     * @param oldScene the scene to fade out
     * @param newScene the scene to fade in (textures should not be initialized yet)
     */
    public void startTransition(Scene oldScene, Scene newScene) {
        if (isTransitioning()) {
            Log.w(TAG, "Transition already in progress, ignoring new transition request");
            return;
        }

        this.oldScene = oldScene;
        this.newScene = newScene;
        this.state = TransitionState.PRELOADING_TEXTURES;

        Log.d(TAG, "Started scene transition: " + oldScene.getSceneName() + " -> " + newScene.getSceneName());
    }

    /**
     * Update the transition state. Call this every frame from onDrawFrame.
     * Returns the scene that should be rendered.
     * Manages texture preloading completion and alpha fading.
     *
     * @param currentlyActiveScene the scene currently being rendered
     * @return the scene to render this frame
     */
    public Scene updateTransition(Scene currentlyActiveScene) {
        if (!isTransitioning()) {
            return currentlyActiveScene;
        }

        long currentTimeMs = System.currentTimeMillis();

        switch (state) {
            case PRELOADING_TEXTURES:
                // Check if new scene textures have been loaded
                if (newScene.isInitialized()) {
                    // Textures are ready, begin the crossfade
                    fadeStartTimeMs = currentTimeMs;
                    state = TransitionState.FADING;
                    Log.d(TAG, "Textures preloaded, beginning crossfade");
                }
                // Continue rendering old scene during preload
                return oldScene;

            case FADING:
                // Calculate fade progress (0.0 to 1.0)
                long elapsedMs = currentTimeMs - fadeStartTimeMs;
                float fadeProgress = Math.min(1.0f, (float) elapsedMs / FADE_DURATION_MS);

                // Update alpha values for both scenes
                setSceneAlpha(oldScene, 1.0f - fadeProgress);  // Old scene fades out
                setSceneAlpha(newScene, fadeProgress);          // New scene fades in

                Log.d(TAG, "Crossfading: progress=" + String.format("%.2f", fadeProgress * 100) + "%");

                // Check if fade is complete
                if (fadeProgress >= 1.0f) {
                    state = TransitionState.COMPLETE;
                    Log.d(TAG, "Crossfade complete, switching to new scene");
                    return newScene;
                }

                // During fade, render old scene (it will have both old and new visible)
                return oldScene;

            case COMPLETE:
                // Transition is complete, reset state for next transition
                state = TransitionState.IDLE;
                Log.d(TAG, "Scene transition finished");
                return newScene;

            default:
                return currentlyActiveScene;
        }
    }

    /**
     * Set the alpha value for all sprites in a scene.
     *
     * @param scene the scene to modify
     * @param alpha the alpha value (0.0 to 1.0)
     */
    private void setSceneAlpha(Scene scene, float alpha) {
        for (Sprite sprite : scene.getSprites()) {
            sprite.setAlpha(alpha);
        }
    }

    /**
     * Get the old scene (useful for rendering during transition).
     */
    public Scene getOldScene() {
        return oldScene;
    }

    /**
     * Get the new scene (useful for rendering during transition).
     */
    public Scene getNewScene() {
        return newScene;
    }
}

