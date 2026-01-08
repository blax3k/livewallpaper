package com.example.livewallpaper;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for TimeBasedInterpolator utility class.
 * Tests time-based linear interpolation behavior.
 */
public class TimeBasedInterpolatorTest {

    private static final float EPSILON = 0.01f;

    // ==================== calculateDeltaTime Tests ====================

    @Test
    public void calculateDeltaTime_WithInvalidLastTime_ReturnsDefaultFPS() {
        float dt = TimeBasedInterpolator.calculateDeltaTime(1_000_000_000L, -1L);
        assertEquals(1f / 60f, dt, EPSILON);

        dt = TimeBasedInterpolator.calculateDeltaTime(1_000_000_000L, 0L);
        assertEquals(1f / 60f, dt, EPSILON);
    }

    @Test
    public void calculateDeltaTime_WithZeroDelta_ReturnsDefaultFPS() {
        float dt = TimeBasedInterpolator.calculateDeltaTime(1_000_000_000L, 1_000_000_000L);
        assertEquals(1f / 60f, dt, EPSILON);
    }

    @Test
    public void calculateDeltaTime_ProducesPositiveValues() {
        // Test that it produces reasonable positive delta times
        float dt = TimeBasedInterpolator.calculateDeltaTime(2_000_000_000L, 1_000_000_000L);
        assertTrue("Should produce positive delta time", dt > 0);
    }

    // ==================== interpolateTowardsTarget Tests ====================

    @Test
    public void interpolateTowardsTarget_WithNoDistance_ReturnsTarget() {
        float result = TimeBasedInterpolator.interpolateTowardsTarget(5.0f, 5.0f, 0.016f, 0.1f);
        assertEquals(5.0f, result, EPSILON);
    }

    @Test
    public void interpolateTowardsTarget_WithZeroDuration_SnapsToTarget() {
        float result = TimeBasedInterpolator.interpolateTowardsTarget(0.0f, 10.0f, 0.016f, 0.0f);
        assertEquals(10.0f, result, EPSILON);

        result = TimeBasedInterpolator.interpolateTowardsTarget(0.0f, 10.0f, 0.016f, -0.1f);
        assertEquals(10.0f, result, EPSILON);
    }

    @Test
    public void interpolateTowardsTarget_InterpolatesPartially() {
        // Moving from 0 to 10: alpha = min(1, 0.016 / 0.1) = 0.16
        // newValue = 0 + (10 - 0) * 0.16 = 1.6
        float result = TimeBasedInterpolator.interpolateTowardsTarget(0.0f, 10.0f, 0.016f, 0.1f);
        assertTrue("Should move towards target", result > 0.0f && result < 10.0f);
        assertTrue("Value should be approximately 1.6", Math.abs(result - 1.6f) < 0.1f);
    }

    @Test
    public void interpolateTowardsTarget_WithLargeDeltaTime_ReachesTarget() {
        // alpha = min(1, 0.2 / 0.1) = 1.0 -> reaches target
        float result = TimeBasedInterpolator.interpolateTowardsTarget(0.0f, 10.0f, 0.2f, 0.1f);
        assertEquals(10.0f, result, EPSILON);
    }

    @Test
    public void interpolateTowardsTarget_MovesMonotonicallyTowardsTarget() {
        float current = 0.0f;
        float target = 10.0f;
        float dt = 0.016f;
        float duration = 0.1f;

        // First frame should move towards target
        float frame1 = TimeBasedInterpolator.interpolateTowardsTarget(current, target, dt, duration);
        assertTrue("Should move from 0 towards 10", frame1 > 0.0f && frame1 < 10.0f);

        // Second frame should move further (monotonic)
        float frame2 = TimeBasedInterpolator.interpolateTowardsTarget(frame1, target, dt, duration);
        assertTrue("Should continue moving", frame2 > frame1);

        // Third frame should also continue moving forward or reach target
        float frame3 = TimeBasedInterpolator.interpolateTowardsTarget(frame2, target, dt, duration);
        assertTrue("Should move further or reach target", frame3 >= frame2);
    }

    @Test
    public void interpolateTowardsTarget_WithSmallDuration_QuicklyReachesTarget() {
        // alpha = min(1, 0.05 / 0.05) = 1.0 -> instant snap
        float result = TimeBasedInterpolator.interpolateTowardsTarget(0.0f, 10.0f, 0.05f, 0.05f);
        assertEquals(10.0f, result, EPSILON);
    }

    @Test
    public void interpolateTowardsTarget_WithNegativeDistance_InterpolatesCorrectly() {
        // Moving from 10 to 0: newValue = 10 + (0 - 10) * 0.16 = 8.4
        float result = TimeBasedInterpolator.interpolateTowardsTarget(10.0f, 0.0f, 0.016f, 0.1f);
        assertTrue("Should move towards zero", result < 10.0f && result > 0.0f);
    }

    @Test
    public void interpolateTowardsTarget_SnapsWhenVeryClose() {
        // Within snap threshold (0.001f)
        float result = TimeBasedInterpolator.interpolateTowardsTarget(9.9995f, 10.0f, 0.016f, 0.1f);
        assertEquals(10.0f, result, EPSILON);
    }
}

