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
    private Scene transitionScene;
    private long fadeStartTimeMs = 0;
    private java.util.Set<Sprite> fadeOutSprites = new java.util.HashSet<>();
    private java.util.Set<Sprite> fadeInSprites = new java.util.HashSet<>();

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
        this.transitionScene = null;
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

                    // Create the combined transition scene
                    transitionScene = createTransitionScene();

                    Log.d(TAG, "Textures preloaded, beginning crossfade");
                }
                // Continue rendering old scene during preload
                return oldScene;

            case FADING:
                // Calculate fade progress (0.0 to 1.0)
                long elapsedMs = currentTimeMs - fadeStartTimeMs;
                float fadeProgress = Math.min(1.0f, (float) elapsedMs / FADE_DURATION_MS);

                // Update alpha values only for non-duplicate sprites
                updateFadeAlpha(fadeProgress);

                Log.d(TAG, "Crossfading: progress=" + String.format("%.2f", fadeProgress * 100) + "%");

                // Check if fade is complete
                if (fadeProgress >= 1.0f) {
                    state = TransitionState.COMPLETE;
                    Log.d(TAG, "Crossfade complete, switching to new scene");
                    return newScene;
                }

                // During fade, render the combined transition scene
                return transitionScene;

            case COMPLETE:
                // Transition is complete, reset state for next transition
                // Reset alpha values for all affected sprites
                for (Sprite sprite : fadeOutSprites) {
                    sprite.setAlpha(1.0f);
                }
                for (Sprite sprite : fadeInSprites) {
                    sprite.setAlpha(1.0f);
                }
                fadeOutSprites.clear();
                fadeInSprites.clear();

                state = TransitionState.IDLE;
                transitionScene = null;
                Log.d(TAG, "Scene transition finished");
                return newScene;

            default:
                return currentlyActiveScene;
        }
    }

    /**
     * Update alpha values for sprites during the fade transition.
     * Only fades sprites that are unique to each scene, keeping duplicates at full alpha.
     *
     * @param fadeProgress the fade progress (0.0 to 1.0)
     */
    private void updateFadeAlpha(float fadeProgress) {
        // Fade out sprites unique to old scene
        for (Sprite sprite : fadeOutSprites) {
            sprite.setAlpha(1.0f - fadeProgress);
        }

        // Fade in sprites unique to new scene
        for (Sprite sprite : fadeInSprites) {
            sprite.setAlpha(fadeProgress);
        }
    }

    /**
     * Check if two sprites are duplicates (have matching properties).
     * Compares texture ID, position, and other relevant properties.
     *
     * @param sprite1 first sprite
     * @param sprite2 second sprite
     * @return true if sprites are duplicates, false otherwise
     */
    private boolean areSpriteDuplicates(Sprite sprite1, Sprite sprite2) {
        return sprite1.getTextureResourceId() == sprite2.getTextureResourceId() &&
               Math.abs(sprite1.getPositionX() - sprite2.getPositionX()) < 0.001f &&
               Math.abs(sprite1.getPositionY() - sprite2.getPositionY()) < 0.001f &&
               sprite1.getTextureId() == sprite2.getTextureId();
    }

    /**
     * Create a transition scene combining sprites from both old and new scenes.
     * Avoids adding duplicate sprites where all properties match.
     * Identifies which sprites should fade and which should remain at full alpha.
     *
     * @return the combined transition scene
     */
    private Scene createTransitionScene() {
        Scene combinedScene = new Scene("TransitionScene");
        fadeOutSprites.clear();
        fadeInSprites.clear();

        // Add all sprites from old scene and mark non-duplicates for fade out
        for (Sprite oldSprite : oldScene.getSprites()) {
            combinedScene.addSprite(oldSprite);

            // Check if this sprite has a duplicate in new scene
            boolean hasDuplicate = false;
            for (Sprite newSprite : newScene.getSprites()) {
                if (areSpriteDuplicates(oldSprite, newSprite)) {
                    hasDuplicate = true;
                    break;
                }
            }

            // Only fade out sprites that don't exist in new scene
            if (!hasDuplicate) {
                fadeOutSprites.add(oldSprite);
            } else {
                // Keep duplicates at full alpha
                oldSprite.setAlpha(1.0f);
            }
        }

        // Add sprites from new scene that aren't duplicates and mark them for fade in
        for (Sprite newSprite : newScene.getSprites()) {
            boolean isDuplicate = false;
            for (Sprite oldSprite : oldScene.getSprites()) {
                if (areSpriteDuplicates(newSprite, oldSprite)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                combinedScene.addSprite(newSprite);
                fadeInSprites.add(newSprite);
            } else {
                // Ensure duplicate sprites in new scene are at full alpha
                newSprite.setAlpha(1.0f);
            }
        }

        Log.d(TAG, "Created transition scene with " + combinedScene.getSprites().size() +
                   " sprites (old: " + oldScene.getSprites().size() +
                   ", new: " + newScene.getSprites().size() +
                   ", fadeOut: " + fadeOutSprites.size() +
                   ", fadeIn: " + fadeInSprites.size() + ")");

        return combinedScene;
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

