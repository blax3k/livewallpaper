package com.example.livewallpaper.sensors;

/**
 * Manages smooth vertical scroll interpolation for landscape orientation.
 * Symmetric counterpart to ScrollOffsetProcessor: controls the Y-axis offset
 * driven by yFocus (0.0 = top, 0.5 = center, 1.0 = bottom) instead of the
 * X-axis offset driven by xFocus / launcher page scrolling.
 *
 * Only active when the screen is in landscape (width > height). In portrait
 * the Y offset is always 0 and this processor is not queried.
 */
public class VerticalScrollOffsetProcessor {

    // Current displayed vertical offset (world units). Sent to the GPU each frame.
    private float currentOffset = 0f;
    // Target vertical offset we will smoothly approach over several frames.
    private float targetOffset = 0f;
    // Scale for converting yFocus [0..1] into world units — matches ScrollOffsetProcessor.
    private static final float SCROLL_SCALE = 5.0f;
    // Transition duration synchronized with ScrollOffsetProcessor.XFOCUS_SMOOTHING_DURATION.
    private static final float YFOCUS_SMOOTHING_DURATION = 1.0f;
    // Last frame timestamp (nanoseconds) for delta-time computation.
    private volatile long lastFrameTimeNs = -1L;

    /**
     * Update the vertical offset for the current frame.
     * Should be called once per frame from the render thread when in landscape.
     *
     * @return the interpolated current vertical offset to send to the GPU
     */
    public float updateAndGetCurrentOffset() {
        long nowNs = System.nanoTime();
        float dt = TimeBasedInterpolator.calculateDeltaTime(nowNs, lastFrameTimeNs);
        lastFrameTimeNs = nowNs;

        currentOffset = TimeBasedInterpolator.interpolateTowardsTargetEased(
                currentOffset, targetOffset, dt, YFOCUS_SMOOTHING_DURATION);

        return currentOffset;
    }

    /**
     * Set the vertical scroll target from a yFocus value using eased interpolation.
     *
     * @param yFocus the yFocus offset in [0..1] (0.0 = top, 0.5 = center, 1.0 = bottom)
     */
    public void setScrollTargetFromYFocus(float yFocus) {
        this.targetOffset = calculateOffset(yFocus);
    }

    /**
     * Set the vertical offset immediately (no interpolation).
     * Used when initialising a scene in landscape mode.
     *
     * @param offset the vertical offset in world units
     */
    public void setOffsetImmediate(float offset) {
        this.currentOffset = offset;
        this.targetOffset = offset;
    }

    /**
     * Called when the renderer resumes. Invalidates the frame timer so the first
     * frame uses a safe default dt instead of a large stale elapsed time.
     */
    public void onRendererResume() {
        lastFrameTimeNs = -1L;
    }

    /**
     * Called when the renderer pauses.
     */
    public void onRendererPause() {
        lastFrameTimeNs = -1L;
    }

    /**
     * Convert a yFocus value [0..1] to world-space vertical offset units.
     * yFocus=0.5 is centered (returns 0). yFocus > 0.5 moves content up (negative),
     * yFocus < 0.5 moves content down (positive) — mirrors ScrollOffsetProcessor's X logic.
     */
    private float calculateOffset(float yFocus) {
        return (0.5f - yFocus) * SCROLL_SCALE;
    }
}
