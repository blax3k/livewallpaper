package com.example.livewallpaper.sensors;

/**
 * Utility class for calculating sprite scale factors based on gyroscope motion.
 * When gyro motion is enabled, sprites need to be enlarged to account for the
 * expanded range of motion, preventing sprite edges from entering the visible area.
 */
public class GyroScaleCalculator {

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
}

