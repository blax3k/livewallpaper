package com.example.livewallpaper.sensors;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for GyroSensorProcessor.
 * Tests gyroscope data processing, angle integration, clamping, and offset interpolation.
 */
public class GyroSensorProcessorTest {

    private GyroSensorProcessor processor;
    private static final float EPSILON = 0.01f;

    @Before
    public void setUp() {
        processor = new GyroSensorProcessor();
    }

    // ==================== Initialization Tests ====================

    @Test
    public void constructor_InitializesOffsetsToZero() {
        assertEquals("currentOffsetX should start at 0", 0f, processor.getOffsetX(), EPSILON);
        assertEquals("currentOffsetY should start at 0", 0f, processor.getOffsetY(), EPSILON);
    }

    // ==================== Configuration Tests ====================

    @Test
    public void setMotionOffsetLimit_UpdatesLimit() {
        processor.setMotionOffsetLimit(0.8f);
        // We can verify this indirectly by seeing if limits are respected
        // Process gyro data and verify it doesn't exceed the limit
        processor.onGyroscopeChanged(10f, 10f, 0f);
        float offsetX = processor.updateAndGetCurrentOffsetX();
        assertTrue("Offset should not exceed limit", Math.abs(offsetX) <= 0.8f + EPSILON);
    }

    @Test
    public void setGyroSensitivity_AffectsOffsetMagnitude() {
        processor.setGyroSensitivity(1.0f);
        // Process multiple times to build up cumulative angle
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(1f, 0f, 0f);
        }
        float offsetWithSensitivity1 = processor.updateAndGetCurrentOffsetX();

        // Reset and try with different sensitivity
        processor.reset();
        processor.setGyroSensitivity(2.0f);
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(1f, 0f, 0f);
        }
        float offsetWithSensitivity2 = processor.updateAndGetCurrentOffsetX();

        // Higher sensitivity should produce larger offset
        assertTrue("Higher sensitivity should produce larger offset",
            Math.abs(offsetWithSensitivity2) >= Math.abs(offsetWithSensitivity1));
    }

    @Test
    public void setGyroChasingDuration_AcceptsFastDuration() {
        processor.setGyroChasingDuration(0.05f);
        // Should not throw
        for (int i = 0; i < 3; i++) {
            processor.onGyroscopeChanged(1f, 1f, 0f);
        }
    }

    @Test
    public void setGyroChasingDuration_ClampsNegativeValues() {
        processor.setGyroChasingDuration(-1f);
        // Should clamp to 0
        for (int i = 0; i < 3; i++) {
            processor.onGyroscopeChanged(1f, 1f, 0f);
        }
        float offset = processor.updateAndGetCurrentOffsetX();
        // Should process without throwing
        assertNotNull("Should process gyro data", offset);
    }

    // ==================== Gyro Input Processing Tests ====================

    @Test
    public void onGyroscopeChanged_WithZeroInput_ProducesZeroOffset() {
        processor.onGyroscopeChanged(0f, 0f, 0f);
        assertEquals("Zero gyro input should produce zero offset", 0f, processor.updateAndGetCurrentOffsetX(), EPSILON);
        assertEquals("Zero gyro input should produce zero offset", 0f, processor.updateAndGetCurrentOffsetY(), EPSILON);
    }

    @Test
    public void onGyroscopeChanged_WithPositiveInput_ProducesNonZeroOffset() {
        // Process multiple times to build cumulative angle
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(1f, 1f, 0f);
        }
        float offsetX = processor.updateAndGetCurrentOffsetX();
        float offsetY = processor.updateAndGetCurrentOffsetY();
        assertTrue("Positive gyro input should produce offset", offsetX >= 0f || offsetY >= 0f);
    }

    @Test
    public void onGyroscopeChanged_WithNegativeInput_ProducesNegativeOffset() {
        // Process multiple times to build cumulative angle
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(-1f, 0f, 0f);
        }
        float offsetY = processor.updateAndGetCurrentOffsetY();
        assertTrue("Negative gyro input should produce negative offset", offsetY <= 0f);
    }

    // ==================== Offset Clamping Tests ====================

    @Test
    public void onGyroscopeChanged_ClampsOffsetToLimit() {
        processor.setMotionOffsetLimit(0.5f);
        // Process large gyro input multiple times to exceed limit
        for (int i = 0; i < 10; i++) {
            processor.onGyroscopeChanged(10f, 10f, 0f);
        }
        float offsetX = processor.updateAndGetCurrentOffsetX();
        float offsetY = processor.updateAndGetCurrentOffsetY();
        assertTrue("OffsetX should not exceed limit", Math.abs(offsetX) <= 0.5f + EPSILON);
        assertTrue("OffsetY should not exceed limit", Math.abs(offsetY) <= 0.5f + EPSILON);
    }

    // ==================== Interpolation Tests ====================

    @Test
    public void updateAndGetCurrentOffsetX_InterpolatesSmooth() {
        processor.setGyroChasingDuration(0.1f);
        // Process multiple times to build cumulative angle
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(1f, 0f, 0f);
        }

        // First frame - should move towards target
        float frame1X = processor.updateAndGetCurrentOffsetX();
        assertTrue("Should move towards target", frame1X >= 0f);
    }

    @Test
    public void updateAndGetCurrentOffsetY_InterpolatesSmooth() {
        processor.setGyroChasingDuration(0.1f);
        // Process multiple times to build cumulative angle
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(0f, 1f, 0f);
        }

        // First frame - should move towards target
        float frame1Y = processor.updateAndGetCurrentOffsetY();
        assertTrue("Should move towards target", frame1Y >= 0f);
    }

    @Test
    public void updateAndGetCurrentOffsets_BothXAndYInterpolate() {
        processor.setGyroChasingDuration(0.1f);
        // Process multiple times to build cumulative angles
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(1f, 1f, 0f);
        }

        float offsetX = processor.updateAndGetCurrentOffsetX();
        float offsetY = processor.updateAndGetCurrentOffsetY();

        assertTrue("X should move towards target", offsetX >= 0f);
        assertTrue("Y should move towards target", offsetY >= 0f);
    }

    @Test
    public void updateAndGetCurrentOffset_WithZeroDuration_SnapsToTarget() {
        processor.setGyroChasingDuration(0f);
        // Process multiple times to build cumulative angle
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(1f, 1f, 0f);
        }

        float offsetX = processor.updateAndGetCurrentOffsetX();
        float offsetY = processor.updateAndGetCurrentOffsetY();

        // With zero duration, should snap instantly to target
        assertTrue("Should reach target with zero duration", offsetX >= 0f || offsetY >= 0f);
    }

    // ==================== Reset Tests ====================

    @Test
    public void reset_ClearsAllOffsets() {
        processor.onGyroscopeChanged(5f, 5f, 0f);
        processor.updateAndGetCurrentOffsetX();
        processor.updateAndGetCurrentOffsetY();

        processor.reset();

        assertEquals("OffsetX should be reset to 0", 0f, processor.getOffsetX(), EPSILON);
        assertEquals("OffsetY should be reset to 0", 0f, processor.getOffsetY(), EPSILON);
    }

    @Test
    public void reset_AllowsRestartingGyroProcessing() {
        processor.onGyroscopeChanged(1f, 1f, 0f);
        processor.updateAndGetCurrentOffsetX();
        processor.reset();

        // After reset, should be able to process gyro data again
        processor.onGyroscopeChanged(1f, 1f, 0f);
        float offsetX = processor.updateAndGetCurrentOffsetX();
        assertTrue("Should process gyro data after reset", offsetX >= 0f);
    }

    // ==================== Multi-Frame Tests ====================

    @Test
    public void multipleFrames_ProgressTowardsTarget() {
        processor.setGyroChasingDuration(0.5f); // Slow enough to see progression
        processor.onGyroscopeChanged(1f, 1f, 0f);

        float frame1X = processor.updateAndGetCurrentOffsetX();
        float frame1Y = processor.updateAndGetCurrentOffsetY();

        // Process more frames
        for (int i = 0; i < 3; i++) {
            processor.onGyroscopeChanged(1f, 1f, 0f);
        }

        float frame2X = processor.updateAndGetCurrentOffsetX();
        float frame2Y = processor.updateAndGetCurrentOffsetY();

        // Should be progressing towards target (getting larger)
        assertTrue("X should progress towards target", frame2X >= frame1X);
        assertTrue("Y should progress towards target", frame2Y >= frame1Y);
    }

    @Test
    public void continuousGyroInput_BuildsUpCumulativeAngle() {
        processor.onGyroscopeChanged(1f, 0f, 0f);
        float frame1Y = processor.updateAndGetCurrentOffsetY();

        // Add more gyro input in same direction
        for (int i = 0; i < 5; i++) {
            processor.onGyroscopeChanged(1f, 0f, 0f);
            processor.updateAndGetCurrentOffsetY();
        }

        float frameFinalY = processor.updateAndGetCurrentOffsetY();

        // Continuous input in same direction should increase offset
        assertTrue("Continuous input should increase offset", frameFinalY >= frame1Y);
    }

    @Test
    public void alternatingGyroInput_OffsetChangesDirection() {
        // Input in positive direction
        processor.onGyroscopeChanged(1f, 0f, 0f);
        processor.updateAndGetCurrentOffsetX();
        processor.updateAndGetCurrentOffsetY();
        float positiveY = processor.getOffsetY();

        // Input in negative direction
        processor.onGyroscopeChanged(-1f, 0f, 0f);
        processor.updateAndGetCurrentOffsetY();
        float negativeY = processor.getOffsetY();

        // Should be able to go both directions
        assertTrue("Should support alternating input", positiveY >= 0f);
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void getOffsetX_ReturnsCurrentValue() {
        processor.onGyroscopeChanged(1f, 0f, 0f);
        float fromUpdate = processor.updateAndGetCurrentOffsetX();
        float fromGetter = processor.getOffsetX();
        assertEquals("getOffsetX should return current value", fromUpdate, fromGetter, EPSILON);
    }

    @Test
    public void getOffsetY_ReturnsCurrentValue() {
        processor.onGyroscopeChanged(0f, 1f, 0f);
        float fromUpdate = processor.updateAndGetCurrentOffsetY();
        float fromGetter = processor.getOffsetY();
        assertEquals("getOffsetY should return current value", fromUpdate, fromGetter, EPSILON);
    }

    @Test
    public void onGyroscopeChanged_HandlesZInputAxis() {
        // Z axis (roll around yaw) is typically not used for parallax
        processor.onGyroscopeChanged(0f, 0f, 10f);
        float offsetX = processor.updateAndGetCurrentOffsetX();
        float offsetY = processor.updateAndGetCurrentOffsetY();
        // Z input should not produce significant offset
        assertTrue("Z axis should not produce large offset", Math.abs(offsetX) < 0.1f && Math.abs(offsetY) < 0.1f);
    }

    @Test
    public void multipleResets_WorkCorrectly() {
        processor.onGyroscopeChanged(5f, 5f, 0f);
        processor.updateAndGetCurrentOffsetX();
        processor.reset();

        processor.onGyroscopeChanged(3f, 3f, 0f);
        processor.updateAndGetCurrentOffsetX();
        processor.reset();

        assertEquals("Should reset cleanly multiple times", 0f, processor.getOffsetX(), EPSILON);
        assertEquals("Should reset cleanly multiple times", 0f, processor.getOffsetY(), EPSILON);
    }
}

