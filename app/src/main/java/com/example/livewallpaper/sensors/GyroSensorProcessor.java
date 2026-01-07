package com.example.livewallpaper.sensors;

import android.util.Log;

/**
 * Processes raw gyroscope rotation rates and converts them into stable offsets that can be
 * passed to a renderer. Handles low-pass filtering, integration to cumulative angles, smoothing
 * and guard rails (limits).
 */
public class GyroSensorProcessor {
    private static final String TAG = "GyroSensorProcessor";

    private float[] prevSensorData = new float[3];
    private float cumulativeAngleX = 0f;
    private float cumulativeAngleY = 0f;
    private long lastUpdateTimeNs = 0L;

    private float motionOffsetLimit = 0.5f; // max translation applied to sprites
    private float gyroSensitivity = 0.8f;   // conversion from radians to screen units
    private float smoothingFactor = 0.2f;   // smoothing when moving towards target

    private float currentOffsetX = 0f;
    private float currentOffsetY = 0f;

    public GyroSensorProcessor() {
    }

    public void setMotionOffsetLimit(float limit) {
        this.motionOffsetLimit = limit;
    }

    public void setGyroSensitivity(float sensitivity) {
        this.gyroSensitivity = sensitivity;
    }

    public void setSmoothingFactor(float smoothingFactor) {
        this.smoothingFactor = smoothingFactor;
    }

    /**
     * Call this with raw rotation rates from the gyroscope (radians/sec) for each axis.
     * Returns true if offsets changed meaningfully.
     */
    public boolean onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        try {
            long now = System.nanoTime();
            float deltaTime = lastUpdateTimeNs == 0 ? 0f : (now - lastUpdateTimeNs) / 1_000_000_000.0f;
            lastUpdateTimeNs = now;

            if (deltaTime > 0.1f) deltaTime = 0.1f; // cap

            float[] raw = new float[] { rotationX, rotationY, rotationZ };

            float[] lp = lowPass(raw, prevSensorData);
            prevSensorData = raw;

            // integrate
            cumulativeAngleX += lp[0] * deltaTime;
            cumulativeAngleY += lp[1] * deltaTime;

            // guard rails on cumulative angles so that offsets are bounded
            float angleLimit = motionOffsetLimit / Math.max(gyroSensitivity, 1e-6f);
            if (cumulativeAngleX > angleLimit) cumulativeAngleX = angleLimit;
            else if (cumulativeAngleX < -angleLimit) cumulativeAngleX = -angleLimit;

            if (cumulativeAngleY > angleLimit) cumulativeAngleY = angleLimit;
            else if (cumulativeAngleY < -angleLimit) cumulativeAngleY = -angleLimit;

            float targetX = cumulativeAngleY * gyroSensitivity; // roll -> x
            float targetY = cumulativeAngleX * gyroSensitivity; // pitch -> y

            // clamp targets
            if (targetX > motionOffsetLimit) targetX = motionOffsetLimit;
            else if (targetX < -motionOffsetLimit) targetX = -motionOffsetLimit;
            if (targetY > motionOffsetLimit) targetY = motionOffsetLimit;
            else if (targetY < -motionOffsetLimit) targetY = -motionOffsetLimit;

            // smoothing
            float newX = currentOffsetX * (1.0f - smoothingFactor) + targetX * smoothingFactor;
            float newY = currentOffsetY * (1.0f - smoothingFactor) + targetY * smoothingFactor;

            boolean changed = Math.abs(newX - currentOffsetX) > 1e-6f || Math.abs(newY - currentOffsetY) > 1e-6f;
            currentOffsetX = newX;
            currentOffsetY = newY;

            return changed;
        } catch (Exception e) {
            Log.e(TAG, "Error processing gyro: " + e.getMessage(), e);
            return false;
        }
    }

    public float getOffsetX() {
        return currentOffsetX;
    }

    public float getOffsetY() {
        return currentOffsetY;
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

