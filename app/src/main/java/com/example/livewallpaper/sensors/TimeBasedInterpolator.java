package com.example.livewallpaper.sensors;

/**
 * Utility class for time-based linear interpolation (chasing).
 * Provides common interpolation logic used by ScrollOffsetInterpolator and GyroSensorProcessor.
 */
public class TimeBasedInterpolator {
    private static final float DEFAULT_FPS = 60f;
    private static final float SNAP_THRESHOLD = 0.001f;
    private static final float DIFFERENCE_THRESHOLD = 0.00001f;

    /**
     * Calculate delta time in seconds from two nanosecond timestamps.
     *
     * @param currentTimeNs current timestamp in nanoseconds
     * @param lastTimeNs    last recorded timestamp in nanoseconds (use -1 or 0 to default to 1/60 FPS)
     * @return delta time in seconds, clamped to reasonable bounds
     */
    public static float calculateDeltaTime(long currentTimeNs, long lastTimeNs) {
        if (lastTimeNs <= 0L) {
            return 1f / DEFAULT_FPS;
        }
        float dt = (currentTimeNs - lastTimeNs) / 1_000_000_000f;
        if (dt <= 0f) {
            return 1f / DEFAULT_FPS;
        }
        // Cap at 100ms to handle frame skips
        if (dt > 0.1f) {
            dt = 0.1f;
        }
        return dt;
    }

    /**
     * Interpolate a value towards a target using time-based linear chasing.
     * The value will reach the target in approximately `duration` seconds.
     *
     * @param current  the current value
     * @param target   the target value to chase towards
     * @param dt       delta time in seconds since last interpolation
     * @param duration duration in seconds to reach target (if <= 0, snaps instantly)
     * @return interpolated value
     */
    public static float interpolateTowardsTarget(float current, float target, float dt, float duration) {
        float diff = target - current;

        // Early exit if no meaningful difference
        if (Math.abs(diff) <= DIFFERENCE_THRESHOLD) {
            return target;
        }

        // Calculate interpolation factor (alpha)
        float alpha = (duration <= 0f) ? 1f : Math.min(1f, dt / duration);
        float newValue = current + diff * alpha;

        // Snap when very close to target to avoid endless tiny adjustments
        if (Math.abs(target - newValue) < SNAP_THRESHOLD) {
            return target;
        }

        return newValue;
    }

    /**
     * Interpolate a value towards a target using time-based easing (ease-out cubic).
     * The value will accelerate quickly towards the target, then slow down as it approaches.
     * This provides smoother, more natural motion than linear interpolation.
     * The value will reach the target in approximately `duration` seconds.
     *
     * @param current  the current value
     * @param target   the target value to chase towards
     * @param dt       delta time in seconds since last interpolation
     * @param duration duration in seconds to reach target (if <= 0, snaps instantly)
     * @return interpolated value
     */
    public static float interpolateTowardsTargetEased(float current, float target, float dt, float duration) {
        float diff = target - current;

        // Early exit if no meaningful difference
        if (Math.abs(diff) <= DIFFERENCE_THRESHOLD) {
            return target;
        }

        // Calculate raw interpolation factor (0.0 to 1.0)
        float t = (duration <= 0f) ? 1f : Math.min(1f, dt / duration);

        // Apply ease-out cubic easing: 1 - (1-t)^3
        // This accelerates quickly at first (t=0 has high derivative) and slows down near target
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);

        float newValue = current + diff * eased;

        // Snap when very close to target to avoid endless tiny adjustments
        if (Math.abs(target - newValue) < SNAP_THRESHOLD) {
            return target;
        }

        return newValue;
    }
}

