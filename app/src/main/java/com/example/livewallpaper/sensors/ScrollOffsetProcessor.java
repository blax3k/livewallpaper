package com.example.livewallpaper.sensors;

/**
 * Manages smooth scrolling interpolation with time-based easing.
 * Maintains current and target scroll offsets and interpolates between them
 * over a configurable duration. Handles pause/resume lifecycle to avoid snapping.
 */
public class ScrollOffsetProcessor {
    private static final String TAG = "ScrollOffsetProcessor";

    // Current displayed scroll offset (world units). This is the value sent to the GPU each frame.
    private float currentScrollOffset = 0f;
    // Target scroll offset we will smoothly approach over several frames.
    private float targetScrollOffset = 0f;
    // Scale for converting wallpaper offset [0..1] into world units
    private static final float SCROLL_SCALE = 5.0f;
    // Time-based smoothing: duration in seconds over which we'll reach the target. Default ~120ms for user input.
    private float scrollSmoothingDuration = 0.05f;
    // Duration for xFocus transitions: longer for smoother, more deliberate motion. Default ~1.5 seconds.
    private static final float XFOCUS_SMOOTHING_DURATION = 1.5f;
    // Last frame timestamp (nanoseconds) used to compute delta time between frames.
    // Mark volatile because it may be updated from the UI thread (start/stop) while read from the render thread.
    private volatile long lastFrameTimeNs = -1L;
    // Track whether the current scroll target is from xFocus (use eased interpolation)
    // or from user input (use linear interpolation)
    private boolean isXFocusTarget = false;

    /**
     * Update the scroll offset for the current frame based on elapsed time.
     * Should be called once per frame from the render thread.
     *
     * @return the interpolated current scroll offset to send to the GPU
     */
    public float updateAndGetCurrentOffset() {
        // Compute delta time (seconds) since last frame using utility
        long nowNs = System.nanoTime();
        float dt = TimeBasedInterpolator.calculateDeltaTime(nowNs, lastFrameTimeNs);
        lastFrameTimeNs = nowNs;

        // Use eased interpolation for xFocus targets (smoother, accelerate-then-decelerate)
        // Use linear interpolation for user scroll input (responsive, direct)
        if (isXFocusTarget) {
            currentScrollOffset = TimeBasedInterpolator.interpolateTowardsTargetEased(
                currentScrollOffset, targetScrollOffset, dt, XFOCUS_SMOOTHING_DURATION);
        } else {
            currentScrollOffset = TimeBasedInterpolator.interpolateTowardsTarget(
                currentScrollOffset, targetScrollOffset, dt, scrollSmoothingDuration);
        }

        return currentScrollOffset;
    }

    /**
     * Set the scroll target (takes wallpaper offset in [0..1], converts to world units) and will be
     * approached over the next frames using the configured smoothing duration.
     */
    public void setScrollTarget(float offsetX) {
        this.targetScrollOffset = calculateScrollOffset(offsetX);
        this.isXFocusTarget = false;  // User scroll input uses linear interpolation
    }

    /**
     * Set the scroll target from xFocus value using eased interpolation for smoother motion.
     * The value will accelerate quickly towards the target, then slow down near the destination.
     *
     * @param offsetX the xFocus offset in [0..1] (0.0 = left, 0.5 = center, 1.0 = right)
     */
    public void setScrollTargetFromXFocus(float offsetX) {
        this.targetScrollOffset = calculateScrollOffset(offsetX);
        this.isXFocusTarget = true;  // xFocus uses eased interpolation
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
     * Disable scroll motion and smoothly interpolate to neutral position.
     * The sprites will smoothly animate back to their centered position using the existing interpolation.
     */
    public void disableScrollMotion() {
        // Only set the target to neutral; the current offset will smoothly approach it
        // via the normal interpolation logic in updateAndGetCurrentOffset()
        this.targetScrollOffset = calculateScrollOffset(0.5f); // 0.5 is the neutral centered position
    }
}

