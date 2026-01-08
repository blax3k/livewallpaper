package com.example.livewallpaper.sensors;

import android.util.Log;
import com.example.livewallpaper.TimeBassedInterpolator;

/**
 * Processes raw gyroscope rotation rates and converts them into stable offsets that can be
 * passed to a renderer. Handles low-pass filtering, integration to cumulative angles, and
 * time-based interpolation (chasing) towards target offsets.
 */
public class GyroSensorProcessor {
    private static final String TAG = "GyroSensorProcessor";

    private float[] prevSensorData = new float[3];
    private float cumulativeAngleX = 0f;
    private float cumulativeAngleY = 0f;
    private long lastUpdateTimeNs = 0L;

    private float motionOffsetLimit = 0.5f; // max translation applied to sprites
    private float gyroSensitivity = 0.8f;   // conversion from radians to screen units

    // Time-based smoothing: duration in seconds over which we'll reach the target offset.
    // Similar to ScrollOffsetInterpolator's scrollSmoothingDuration.
    private float gyroChasingDuration = 0.12f;

    // Current displayed offsets (what's rendered)
    private float currentOffsetX = 0f;
    private float currentOffsetY = 0f;

    // Target offsets that we're chasing towards (computed from cumulative angles)
    private float targetOffsetX = 0f;
    private float targetOffsetY = 0f;

    // Frame timer for computing delta time for interpolation
    private long lastFrameTimeNs = -1L;
    private float lastFrameDt = 1f / 60f; // Cache the delta time for this frame

    public GyroSensorProcessor() {
    }

    public void setMotionOffsetLimit(float limit) {
        this.motionOffsetLimit = limit;
    }

    public void setGyroSensitivity(float sensitivity) {
        this.gyroSensitivity = sensitivity;
    }

    /**
     * Set the duration (in seconds) over which gyro offsets chase towards their target.
     * Smaller values = snappier response; larger values = smoother, slower response.
     */
    public void setGyroChasingDuration(float seconds) {
        if (seconds < 0f) seconds = 0f;
        this.gyroChasingDuration = seconds;
    }

    /**
     * Call this with raw rotation rates from the gyroscope (radians/sec) for each axis.
     * This updates the target offsets based on cumulative integration.
     */
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        try {
            long now = System.nanoTime();
            float deltaTime = lastUpdateTimeNs == 0 ? 0f : (now - lastUpdateTimeNs) / 1_000_000_000.0f;
            lastUpdateTimeNs = now;

            if (deltaTime > 0.1f) deltaTime = 0.1f; // cap

            float[] raw = new float[] { rotationX, rotationY, rotationZ };

            float[] lp = lowPass(raw, prevSensorData);
            prevSensorData = raw;

            // Integrate rotation rates to cumulative angles
            cumulativeAngleX += lp[0] * deltaTime;
            cumulativeAngleY += lp[1] * deltaTime;

            // Guard rails on cumulative angles so that offsets are bounded
            float angleLimit = motionOffsetLimit / Math.max(gyroSensitivity, 1e-6f);
            if (cumulativeAngleX > angleLimit) cumulativeAngleX = angleLimit;
            else if (cumulativeAngleX < -angleLimit) cumulativeAngleX = -angleLimit;

            if (cumulativeAngleY > angleLimit) cumulativeAngleY = angleLimit;
            else if (cumulativeAngleY < -angleLimit) cumulativeAngleY = -angleLimit;

            // Compute target offsets from cumulative angles
            targetOffsetX = cumulativeAngleY * gyroSensitivity; // roll -> x
            targetOffsetY = cumulativeAngleX * gyroSensitivity; // pitch -> y

            // Clamp targets to limits
            if (targetOffsetX > motionOffsetLimit) targetOffsetX = motionOffsetLimit;
            else if (targetOffsetX < -motionOffsetLimit) targetOffsetX = -motionOffsetLimit;
            if (targetOffsetY > motionOffsetLimit) targetOffsetY = motionOffsetLimit;
            else if (targetOffsetY < -motionOffsetLimit) targetOffsetY = -motionOffsetLimit;
        } catch (Exception e) {
            Log.e(TAG, "Error processing gyro: " + e.getMessage(), e);
        }
    }

    /**
     * Update and get the current interpolated offset for this frame.
     * Should be called once per frame from the render thread.
     * Uses time-based interpolation (chasing) to approach the target offsets.
     *
     * @return the current interpolated X offset
     */
    public float updateAndGetCurrentOffsetX() {
        long nowNs = System.nanoTime();
        lastFrameDt = TimeBassedInterpolator.calculateDeltaTime(nowNs, lastFrameTimeNs);
        lastFrameTimeNs = nowNs;
        return interpolateTowardsTarget(true);
    }

    /**
     * Update and get the current interpolated offset for this frame.
     * Should be called once per frame from the render thread.
     *
     * @return the current interpolated Y offset
     */
    public float updateAndGetCurrentOffsetY() {
        // Don't update frame timer here; it's already updated by updateAndGetCurrentOffsetX()
        return interpolateTowardsTarget(false);
    }

    /**
     * Internal helper to interpolate towards target offset using time-based chasing.
     */
    private float interpolateTowardsTarget(boolean isX) {
        float current = isX ? currentOffsetX : currentOffsetY;
        float target = isX ? targetOffsetX : targetOffsetY;

        // Use utility for time-based interpolation
        float newValue = TimeBassedInterpolator.interpolateTowardsTarget(
            current, target, lastFrameDt, gyroChasingDuration);

        if (isX) {
            currentOffsetX = newValue;
        } else {
            currentOffsetY = newValue;
        }

        return newValue;
    }

    public float getOffsetX() {
        return currentOffsetX;
    }

    public float getOffsetY() {
        return currentOffsetY;
    }

    /**
     * Reset the gyroscope processor to zero offsets and clear accumulated state.
     * Call this when disabling gyro motion to clear any accumulated rotation state.
     */
    public void reset() {
        currentOffsetX = 0f;
        currentOffsetY = 0f;
        targetOffsetX = 0f;
        targetOffsetY = 0f;
        cumulativeAngleX = 0f;
        cumulativeAngleY = 0f;
        lastUpdateTimeNs = 0L;
        lastFrameTimeNs = -1L;
        prevSensorData = new float[3];
    }

    private float[] lowPass(float[] current, float[] previous) {
        float alpha = 0.25f;
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            out[i] = alpha * current[i] + (1.0f - alpha) * previous[i];
        }
        return out;
    }
}

