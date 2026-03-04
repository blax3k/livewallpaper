package com.example.livewallpaper.scene.animation;

/**
 * Encapsulates all wipe animation logic for sprites.
 * Handles wipe progress, direction, and state management during scene transitions.
 */
public class SpriteWipe {
    // Wipe direction constants
    private static final float NO_WIPE = 0.0f;
    private static final float WIPE_OUT_DIRECTION = 1.0f;
    private static final float WIPE_IN_DIRECTION = -1.0f;

    // Wipe progress constants
    private static final float MIN_WIPE_PROGRESS = 0.0f;
    private static final float MAX_WIPE_PROGRESS = 1.0f;
    private static final float DEFAULT_WIPE_PROGRESS = 0.0f;

    // Wipe state
    private float wipeProgress = DEFAULT_WIPE_PROGRESS;
    private float wipeDirection = NO_WIPE;
    private boolean isWipingOut = false;
    private boolean isWipingIn = false;

    /**
     * Get the wipe progress value (0.0 to 1.0).
     */
    public float getWipeProgress() {
        return wipeProgress;
    }

    /**
     * Set the wipe transition progress.
     * Value is clamped between MIN_WIPE_PROGRESS (0.0, no wipe) and MAX_WIPE_PROGRESS (1.0, fully wiped).
     *
     * @param progress the wipe progress value
     */
    public void setWipeProgress(float progress) {
        this.wipeProgress = Math.max(MIN_WIPE_PROGRESS, Math.min(MAX_WIPE_PROGRESS, progress));
    }

    /**
     * Get the wipe direction (-1.0 for wipe in, 0.0 for no wipe, 1.0 for wipe out).
     */
    public float getWipeDirection() {
        return wipeDirection;
    }

    /**
     * Reset wipe state to default (no wipe effect, fully visible).
     */
    public void resetWipe() {
        this.wipeProgress = MIN_WIPE_PROGRESS;
        this.wipeDirection = NO_WIPE;
        this.isWipingOut = false;
        this.isWipingIn = false;
    }

    /**
     * Mark this sprite as wiping out (fading away).
     */
    public void setWipingOut(boolean wipingOut) {
        this.isWipingOut = wipingOut;
        if (wipingOut) {
            this.wipeDirection = WIPE_OUT_DIRECTION;
            this.isWipingIn = false;
        }
    }

    /**
     * Check if this sprite is wiping out.
     */
    public boolean isWipingOut() {
        return isWipingOut;
    }

    /**
     * Mark this sprite as wiping in (fading in).
     */
    public void setWipingIn(boolean wipingIn) {
        this.isWipingIn = wipingIn;
        if (wipingIn) {
            this.wipeDirection = WIPE_IN_DIRECTION;
            this.isWipingOut = false;
        }
    }

    /**
     * Check if this sprite is wiping in.
     */
    public boolean isWipingIn() {
        return isWipingIn;
    }

    /**
     * Check if this sprite is transitioning (either wiping in or out).
     */
    public boolean isTransitioning() {
        return isWipingIn || isWipingOut;
    }
}
