package com.example.livewallpaper.scene.managers;

import android.content.Context;
import com.example.livewallpaper.logging.TimberLog;

import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.Sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages smooth scene transitions with diagonal wipe effects.
 * During a transition:
 * - Sprites common to both scenes remain fully visible
 * - Sprites only in the old scene wipe out (become transparent from top-left to bottom-right)
 * - Sprites only in the new scene wipe in (become visible from top-left to bottom-right)
 *
 * NOTE: FADE_DURATION_MS (800ms) is synchronized with ScrollOffsetProcessor.XFOCUS_SMOOTHING_DURATION
 * so that the xFocus scroll animation and scene transition complete at the same time.
 */
public class SceneTransitionManager {
    private static final String TAG = "SceneTransitionManager";
    // Synchronized with ScrollOffsetProcessor.XFOCUS_SMOOTHING_DURATION (0.8 seconds)
    private static final long FADE_DURATION_MS = 800;

    private enum TransitionState {
        IDLE,
        WAITING_FOR_TEXTURES,
        FADING
    }
    private static final int[] MILESTONES = {0, 20, 40, 60, 80, 100};

    private TransitionState state = TransitionState.IDLE;
    private Scene oldScene;
    private Scene newScene;
    private long fadeStartTimeMs = 0;
    private Context context;

    // Sprites added to oldScene during transition (need to be removed when done)
    private final List<Sprite> addedSprites = new ArrayList<>();

    // ExecutorService for background texture unloading
    private final ExecutorService textureUnloadExecutor = Executors.newSingleThreadExecutor();

    // Timing for frame performance measurement

    public SceneTransitionManager() {
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
    public void startTransition(Scene oldScene, Scene newScene, Context context) {
        if (isTransitioning()) {
            TimberLog.w(TAG, "Transition already in progress, ignoring new transition request");
            return;
        }

        this.oldScene = oldScene;
        this.newScene = newScene;
        this.context = context;
        this.state = TransitionState.WAITING_FOR_TEXTURES;

        TimberLog.d(TAG, "Started transition: " + oldScene.getSceneName() + " -> " + newScene.getSceneName());
    }

    /**
     * Update the transition. Call every frame.
     * Returns the scene to render.
     */
    public Scene updateTransition(TextureManager textureManager) {
        if (state == TransitionState.IDLE) {
            return null;
        }

        switch (state) {
            case WAITING_FOR_TEXTURES:
                // Initialize textures for the new scene on the GL thread
                if (context != null) {
                    newScene.initialize(context, textureManager);
                }
                // Now that textures are ready, begin the fade
                beginFade();
                state = TransitionState.FADING;
            case FADING:
                float progress = calculateProgress();
                oldScene.updateWipeProgress(progress);

                if (progress >= 1.0f) {
                    return finishTransition(textureManager);
                }
            default:
                return oldScene;
        }
    }

    /**
     * Begin the fade by marking all old scene sprites as wiping out
     * and all new scene sprites as wiping in.
     * New sprites get a head start of half the transition duration.
     */
    private void beginFade() {
        fadeStartTimeMs = System.currentTimeMillis();
        addedSprites.clear();

        TimberLog.d(TAG, "=== BEGIN FADE - MARKING ALL SPRITES ===");
        TimberLog.d(TAG, "New sprites fade-in starts immediately (head start: " + (FADE_DURATION_MS / 2) + "ms)");

        // Mark ALL old scene sprites as wiping out
        TimberLog.d(TAG, "--- Wiping out all old scene sprites (" + oldScene.getSprites().size() + ") ---");
        for (Sprite oldSprite : oldScene.getSprites()) {
            oldSprite.setWipingOut(true);
            oldSprite.setWipeProgress(-0.5f);
            TimberLog.d(TAG, "[WIPE OUT] " + oldSprite.getName() +
                  " | textureId=" + oldSprite.getTextureId() +
                  " | pos=(" + String.format("%.2f", oldSprite.getPositionX()) + ", " +
                  String.format("%.2f", oldSprite.getPositionY()) + ")");
        }

        // Mark ALL new scene sprites as wiping in and add them to old scene for rendering
        // Give them a head start by setting their wipe progress to negative (representing time already elapsed)
        TimberLog.d(TAG, "--- Wiping in all new scene sprites (" + newScene.getSprites().size() + ") ---");
        for (Sprite newSprite : newScene.getSprites()) {
            newSprite.setWipingIn(true);
            // Head start: new sprites start at -0.5 progress, so they're already halfway done when old sprites start
            newSprite.setWipeProgress(0.0f);
            oldScene.addSprite(newSprite);
            addedSprites.add(newSprite);
            TimberLog.d(TAG, "[WIPE IN] " + newSprite.getName() +
                  " | textureId=" + newSprite.getTextureId() +
                  " | pos=(" + String.format("%.2f", newSprite.getPositionX()) + ", " +
                  String.format("%.2f", newSprite.getPositionY()) + ") | head-start applied");
        }

        // Sort to maintain proper draw order
        oldScene.sortSpritesByParallax();

        TimberLog.d(TAG, "=== FADE SETUP COMPLETE ===");
        TimberLog.d(TAG, "Total: " + oldScene.getSprites().size() + " sprites in transition");
    }

    private float calculateProgress() {
        long elapsed = System.currentTimeMillis() - fadeStartTimeMs;
        return Math.min(1.0f, (float) elapsed / FADE_DURATION_MS);
    }

    private Scene finishTransition(TextureManager textureManager) {
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
        // This is done on a background thread to avoid blocking the GL thread
        Set<Integer> oldSceneTextureIds = oldScene.getUsedTextureResourceIds();
        Set<Integer> newSceneTextureIds = newScene.getUsedTextureResourceIds();

        textureUnloadExecutor.execute(() -> {
            TimberLog.d(TAG, "Background thread: Starting texture cleanup");
            textureManager.unloadUnusedTextures(oldSceneTextureIds, newSceneTextureIds);
            TimberLog.d(TAG, "Background thread: Texture cleanup complete");
        });

        state = TransitionState.IDLE;

        TimberLog.d(TAG, "Transition finished");
        return newScene;
    }


    public Scene getNewScene() {
        return newScene;
    }
}
