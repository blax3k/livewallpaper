package com.example.livewallpaper;

/**
 * Manages smooth scrolling interpolation with time-based easing.
 * Maintains current and target scroll offsets and interpolates between them
 * over a configurable duration. Handles pause/resume lifecycle to avoid snapping.
 */
public class ScrollOffsetInterpolator {
    private static final String TAG = "ScrollOffsetInterpolator";

    // Current displayed scroll offset (world units). This is the value sent to the GPU each frame.
    private float currentScrollOffset = 0f;
    // Target scroll offset we will smoothly approach over several frames.
    private float targetScrollOffset = 0f;
    // Scale for converting wallpaper offset [0..1] into world units
    private static final float SCROLL_SCALE = 6.0f;
    // Time-based smoothing: duration in seconds over which we'll reach the target. Default ~120ms.
    private float scrollSmoothingDuration = 0.05f;
    // Last frame timestamp (nanoseconds) used to compute delta time between frames.
    // Mark volatile because it may be updated from the UI thread (start/stop) while read from the render thread.
    private volatile long lastFrameTimeNs = -1L;

    /**
     * Update the scroll offset for the current frame based on elapsed time.
     * Should be called once per frame from the render thread.
     *
     * @return the interpolated current scroll offset to send to the GPU
     */
    public float updateAndGetCurrentOffset() {
        // Compute delta time (seconds) since last frame using utility
        long nowNs = System.nanoTime();
        float dt = TimeBassedInterpolator.calculateDeltaTime(nowNs, lastFrameTimeNs);
        lastFrameTimeNs = nowNs;

        // Smoothly approach the target scroll offset using time-based interpolation
        currentScrollOffset = TimeBassedInterpolator.interpolateTowardsTarget(
            currentScrollOffset, targetScrollOffset, dt, scrollSmoothingDuration);

        return currentScrollOffset;
    }

    /**
     * Set the scroll target (takes wallpaper offset in [0..1], converts to world units) and will be
     * approached over the next frames using the configured smoothing duration.
     */
    public void setScrollTarget(float offsetX) {
        this.targetScrollOffset = calculateScrollOffset(offsetX);
    }

    /**
     * Explicitly set both current and target offsets to the same value.
     * Useful for initialization or when you want to snap to a position.
     */
    public void setCurrentAndTargetOffset(float offsetX) {
        float offset = calculateScrollOffset(offsetX);
        this.currentScrollOffset = offset;
        this.targetScrollOffset = offset;
    }

    /**
     * Set smoothing duration in seconds. If set to 0 or negative, scrolling snaps instantly.
     */
    public void setScrollSmoothingDuration(float seconds) {
        if (seconds < 0f) seconds = 0f;
        this.scrollSmoothingDuration = seconds;
    }

    /**
     * Optional: adjust smoothing via a legacy fraction-based API.
     * Range should be (0,1]. Higher smoothing => faster => shorter duration.
     * Deprecated: prefer setScrollSmoothingDuration(float seconds).
     */
    @Deprecated
    public void setScrollSmoothing(float smoothing) {
        // Backwards compatibility: map a [0..1] smoothing fraction to a duration.
        if (smoothing <= 0f) {
            this.scrollSmoothingDuration = 1.0f; // very slow
            return;
        }
        float clamped = Math.min(1f, smoothing);
        // Map: clamped==1 => duration 0 (instant), clamped==0 => 1s (very slow).
        this.scrollSmoothingDuration = Math.max(0f, (1f - clamped));
    }

    /**
     * Called when the renderer is about to start rendering / visibility resumed.
     * Invalidates the frame timer so the first frame uses a small default dt
     * and doesn't snap to the target due to a large elapsed time during pause.
     */
    public void onRendererResume() {
        lastFrameTimeNs = -1L;
    }

    /**
     * Called when the renderer is stopping / visibility lost.
     * Invalidates the last-frame timestamp so a subsequent resume is explicit.
     */
    public void onRendererPause() {
        lastFrameTimeNs = -1L;
    }

    /**
     * Convert wallpaper offset [0..1] to world-space scroll units.
     * offset=0.5 is centered (returns 0). offset > 0.5 moves left (negative), offset < 0.5 moves right (positive).
     */
    private float calculateScrollOffset(float offsetX) {
        return (0.5f - offsetX) * SCROLL_SCALE;
    }

    /**
     * Get the current scroll offset without updating it.
     * Useful for debugging or if you need to read the value without advancing the frame.
     */
    public float getCurrentOffset() {
        return currentScrollOffset;
    }

    /**
     * Get the target scroll offset.
     */
    public float getTargetOffset() {
        return targetScrollOffset;
    }

    /**
     * Disable scroll motion and smoothly interpolate to neutral position.
     * The sprites will smoothly animate back to their centered position using the existing interpolation.
     */
    public void disableScrollMotion() {
        // Only set the target to neutral; the current offset will smoothly approach it
        // via the normal interpolation logic in updateAndGetCurrentOffset()
        this.targetScrollOffset = calculateScrollOffset(0.5f); // 0.5 is the neutral centered position
    }
}

