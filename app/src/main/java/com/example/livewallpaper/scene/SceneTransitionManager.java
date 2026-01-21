package com.example.livewallpaper.scene;

import android.util.Log;

import com.example.livewallpaper.gl.TextureManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages smooth scene transitions with diagonal wipe effects.
 * During a transition:
 * - Sprites common to both scenes remain fully visible
 * - Sprites only in the old scene wipe out (become transparent from top-left to bottom-right)
 * - Sprites only in the new scene wipe in (become visible from top-left to bottom-right)
 */
public class SceneTransitionManager {
    private static final String TAG = "SceneTransitionManager";
    private static final long FADE_DURATION_MS = 500;

    private enum TransitionState {
        IDLE,
        WAITING_FOR_TEXTURES,
        FADING
    }

    private TransitionState state = TransitionState.IDLE;
    private Scene oldScene;
    private Scene newScene;
    private long fadeStartTimeMs = 0;
    private TextureManager textureManager;
    private android.content.Context context;

    // Sprites added to oldScene during transition (need to be removed when done)
    private final List<Sprite> addedSprites = new ArrayList<>();

    public SceneTransitionManager(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public boolean isTransitioning() {
        return state != TransitionState.IDLE;
    }

    /**
     * Start a transition from oldScene to newScene.
     * @param oldScene the currently active scene
     * @param newScene the scene to transition to
     * @param context Android context for texture loading
     */
    public void startTransition(Scene oldScene, Scene newScene, android.content.Context context) {
        if (isTransitioning()) {
            Log.w(TAG, "Transition already in progress, ignoring new transition request");
            return;
        }

        this.oldScene = oldScene;
        this.newScene = newScene;
        this.context = context;
        this.state = TransitionState.WAITING_FOR_TEXTURES;

        Log.d(TAG, "Started transition: " + oldScene.getSceneName() + " -> " + newScene.getSceneName());
    }

    /**
     * Update the transition. Call every frame.
     * Returns the scene to render.
     */
    public Scene updateTransition() {
        if (state == TransitionState.IDLE) {
            return null;
        }

        switch (state) {
            case WAITING_FOR_TEXTURES:
                // Initialize textures for the new scene on the GL thread
                if (textureManager != null && context != null) {
                    newScene.initialize(context, textureManager);
                    Log.d(TAG, "Textures initialized for new scene: " + newScene.getSceneName());
                }
                // Now that textures are ready, begin the fade
                beginFade();
                state = TransitionState.FADING;
                return oldScene;

            case FADING:
                oldScene.sortSpritesByParallax();
                float progress = calculateProgress();
                oldScene.updateWipeProgress(progress);

                Log.d(TAG, "=== FADING PROGRESS: " + String.format("%.2f", progress * 100) + "% ===");
                for (Sprite s : oldScene.getSprites()) {
                    Log.d(TAG, "  sprite: " + s.getName() + " | parallax=" + s.getParallaxMultiplier() +
                          " | wipeProgress=" + String.format("%.2f", s.getWipeProgress()) +
                          " | wipingOut=" + s.isWipingOut() + " | wipingIn=" + s.isWipingIn());
                }

                if (progress >= 1.0f) {
                    return finishTransition();
                }
                return oldScene;

            default:
                return oldScene;
        }
    }

    /**
     * Begin the fade by marking sprites and setting up the scene.
     */
    private void beginFade() {
        fadeStartTimeMs = System.currentTimeMillis();
        addedSprites.clear();

        // Mark sprites unique to old scene as wiping out
        for (Sprite oldSprite : oldScene.getSprites()) {
            if (!hasDuplicateIn(oldSprite, newScene.getSprites())) {
                oldSprite.setWipingOut(true);
                oldSprite.setWipeProgress(0.0f);
            }
        }

        // Mark and add sprites unique to new scene as wiping in
        for (Sprite newSprite : newScene.getSprites()) {
            if (!hasDuplicateIn(newSprite, oldScene.getSprites())) {
                newSprite.setWipingIn(true);
                newSprite.setWipeProgress(0.0f);
                oldScene.addSprite(newSprite);
                addedSprites.add(newSprite);
            }
        }

        // Sort to maintain proper draw order
        oldScene.sortSpritesByParallax();

        Log.d(TAG, "Beginning fade: added " + addedSprites.size() + " sprites from new scene");
    }

    private float calculateProgress() {
        long elapsed = System.currentTimeMillis() - fadeStartTimeMs;
        return Math.min(1.0f, (float) elapsed / FADE_DURATION_MS);
    }

    private Scene finishTransition() {
        // Reset wipe state on all sprites in the new scene
        for (Sprite sprite : newScene.getSprites()) {
            sprite.resetWipe();
        }

        // Remove the sprites we added to oldScene
        for (Sprite sprite : addedSprites) {
            oldScene.getSprites().remove(sprite);
        }
        addedSprites.clear();

        // Clean up textures from old scene that aren't used in new scene
        if (textureManager != null) {
            Set<Integer> oldSceneTextureIds = oldScene.getUsedTextureResourceIds();
            Set<Integer> newSceneTextureIds = newScene.getUsedTextureResourceIds();
            textureManager.unloadUnusedTextures(oldSceneTextureIds, newSceneTextureIds);
            Log.d(TAG, "Cleaned up unused textures from old scene");
        }

        // Sort new scene to maintain consistent draw order
        newScene.sortSpritesByParallax();

        state = TransitionState.IDLE;

        Log.d(TAG, "Transition finished");
        return newScene;
    }

    private boolean hasDuplicateIn(Sprite sprite, List<Sprite> sprites) {
        for (Sprite other : sprites) {
            if (areDuplicates(sprite, other)) {
                return true;
            }
        }
        return false;
    }

    private boolean areDuplicates(Sprite a, Sprite b) {
        return a.getTextureResourceId() == b.getTextureResourceId() &&
               Math.abs(a.getPositionX() - b.getPositionX()) < 0.001f &&
               Math.abs(a.getPositionY() - b.getPositionY()) < 0.001f &&
               a.getTextureId() == b.getTextureId();
    }

    public Scene getNewScene() {
        return newScene;
    }
}
