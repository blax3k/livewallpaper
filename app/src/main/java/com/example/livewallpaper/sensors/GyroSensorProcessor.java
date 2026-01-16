package com.example.livewallpaper.sensors;

import android.util.Log;

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

    // Track whether gyro tracking is paused (e.g., during renderer pause/suspend)
    private boolean isPaused = false;

    public GyroSensorProcessor() {
    }

    /**
     * Calculate the scale factor needed to expand sprites for gyro motion.
     *
     * The scale factor accounts for the maximum gyro motion offset and expands sprites
     * accordingly to prevent visible edges from appearing during device tilt.
     *
     * @param motionOffsetLimit the maximum offset applied by gyro (from GyroSensorProcessor)
     * @param worldHeight the vertical world-space height that maps to screen height
     * @return the scale factor to apply to sprites (1.0 = no scaling, >1.0 = expansion)
     */
    public static float calculateScaleFactor(float motionOffsetLimit, float worldHeight) {
        // Calculate the percentage of screen height that gyro motion can shift
        // Add 1.0 to account for the original size
        return 1.0f + (2.0f * motionOffsetLimit / worldHeight);
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
     * Does nothing if gyro tracking is paused.
     */
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        // Skip processing if paused (e.g., during renderer pause/suspend)
        if (isPaused) {
            return;
        }

        try {
            long now = System.nanoTime();
            float deltaTime = calculateDeltaTime(now);
            lastUpdateTimeNs = now;

            float[] raw = new float[] { rotationX, rotationY, rotationZ };
            float[] filtered = lowPass(raw, prevSensorData);
            prevSensorData = raw;

            integrateAngles(filtered, deltaTime);
            clampCumulativeAngles();
            computeTargetOffsets();
            clampTargetOffsets();
        } catch (Exception e) {
            Log.e(TAG, "Error processing gyro: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate delta time since last gyro update.
     *
     * @param currentTimeNs current time in nanoseconds
     * @return delta time in seconds, capped at 100ms
     */
    private float calculateDeltaTime(long currentTimeNs) {
        float deltaTime = lastUpdateTimeNs == 0 ? 0f : (currentTimeNs - lastUpdateTimeNs) / 1_000_000_000.0f;
        if (deltaTime > 0.1f) deltaTime = 0.1f;
        return deltaTime;
    }

    /**
     * Integrate filtered rotation rates into cumulative angles.
     *
     * @param filteredRates the low-pass filtered rotation rates [x, y, z]
     * @param deltaTime     delta time since last update in seconds
     */
    private void integrateAngles(float[] filteredRates, float deltaTime) {
        cumulativeAngleX += filteredRates[0] * deltaTime;
        cumulativeAngleY += filteredRates[1] * deltaTime;
    }

    /**
     * Apply guard rails to cumulative angles to prevent unbounded rotation.
     */
    private void clampCumulativeAngles() {
        float angleLimit = motionOffsetLimit / Math.max(gyroSensitivity, 1e-6f);
        cumulativeAngleX = Math.max(-angleLimit, Math.min(angleLimit, cumulativeAngleX));
        cumulativeAngleY = Math.max(-angleLimit, Math.min(angleLimit, cumulativeAngleY));
    }

    /**
     * Compute target offsets from cumulative angles using gyro sensitivity.
     */
    private void computeTargetOffsets() {
        targetOffsetX = cumulativeAngleY * gyroSensitivity; // roll -> x
        targetOffsetY = cumulativeAngleX * gyroSensitivity; // pitch -> y
    }

    /**
     * Clamp target offsets to motion limits.
     */
    private void clampTargetOffsets() {
        targetOffsetX = Math.max(-motionOffsetLimit, Math.min(motionOffsetLimit, targetOffsetX));
        targetOffsetY = Math.max(-motionOffsetLimit, Math.min(motionOffsetLimit, targetOffsetY));
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
        lastFrameDt = TimeBasedInterpolator.calculateDeltaTime(nowNs, lastFrameTimeNs);
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
        float newValue = TimeBasedInterpolator.interpolateTowardsTarget(
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
     * Pause gyro tracking. Sensor data will still arrive but will be ignored until resumed.
     * This prevents gyro motion jumps when the renderer pauses and resumes.
     */
    public void pause() {
        isPaused = true;
        Log.d(TAG, "Gyro tracking paused");
    }

    /**
     * Resume gyro tracking from the current position. Accumulated offsets are preserved.
     */
    public void resume() {
        isPaused = false;
        lastUpdateTimeNs = 0L; // Reset timing so next sensor event doesn't create a large delta
        Log.d(TAG, "Gyro tracking resumed");
    }

    /**
     * Check if gyro tracking is currently paused.
     *
     * @return true if paused, false if actively tracking
     */
    public boolean isPaused() {
        return isPaused;
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

    /**
     * Update gyro offsets and apply them as uniforms if gyro motion is enabled.
     * Also handles sprite scaling for gyro motion to prevent visible edges.
     *
     * @param handles the shader handles for setting gyro uniforms
     * @param scene the current scene to apply gyro scaling
     * @param worldHeight the world-space height for scale factor calculation
     * @param spritesScaledForGyro current scaling state (will be mutated)
     * @return the updated spritesScaledForGyro state
     */
    public boolean updateAndApplyGyroUniforms(com.example.livewallpaper.gl.Handles handles,
                                               com.example.livewallpaper.scene.Scene scene,
                                               float worldHeight,
                                               boolean spritesScaledForGyro) {
        if (MotionConfig.isGyroMotionEnabled()) {
            float gyroOffsetX = updateAndGetCurrentOffsetX();
            float gyroOffsetY = updateAndGetCurrentOffsetY();
            android.opengl.GLES20.glUniform1f(handles.gyroOffsetXHandle, gyroOffsetX);
            android.opengl.GLES20.glUniform1f(handles.gyroOffsetYHandle, gyroOffsetY);

            // Apply sprite scaling for gyro motion if not already scaled
            if (!spritesScaledForGyro) {
                float scaleFactor = calculateScaleFactor(motionOffsetLimit, worldHeight);
                scene.applyGyroScaling(scaleFactor);
                spritesScaledForGyro = true;
            }
        } else {
            // When gyro is disabled, set offsets to zero
            android.opengl.GLES20.glUniform1f(handles.gyroOffsetXHandle, 0f);
            android.opengl.GLES20.glUniform1f(handles.gyroOffsetYHandle, 0f);

            // Reset sprite scaling if previously scaled
            if (spritesScaledForGyro) {
                scene.resetGyroScaling();
                spritesScaledForGyro = false;
            }
        }
        return spritesScaledForGyro;
    }

    /**
     * Apply current gyro scaling state to a new scene during transitions.
     * If the processor has an active scale factor, it will be applied to the scene
     * for initialization.
     *
     * @param newScene the scene to apply gyro scaling to
     * @param worldHeight the world-space height for scale factor calculation
     */
    public void applyGyroScalingToNewScene(com.example.livewallpaper.scene.Scene newScene, float worldHeight) {
            float scaleFactor = calculateScaleFactor(motionOffsetLimit, worldHeight);
            newScene.setGyroScalingForInitialization(scaleFactor);
            android.util.Log.d("GyroSensorProcessor", "New scene will be initialized with gyro scaling factor: " + scaleFactor);
    }
}

