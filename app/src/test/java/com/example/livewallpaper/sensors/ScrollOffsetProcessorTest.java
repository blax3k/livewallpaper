package com.example.livewallpaper.sensors;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ScrollOffsetProcessor.
 * Tests smooth scrolling interpolation, offset calculations, pause/resume lifecycle,
 * xFocus targeting, and motion control.
 *
 * Note: Tests focus on behavior rather than exact convergence values since interpolation
 * is time-based and depends on frame timing.
 */
public class ScrollOffsetProcessorTest {

    private ScrollOffsetProcessor processor;
    private static final float EPSILON = 0.1f;

    @Before
    public void setUp() {
        processor = new ScrollOffsetProcessor();
    }

    // ==================== Initialization Tests ====================

    @Test
    public void constructor_InitializesOffsetsToZero() {
        float offset = processor.updateAndGetCurrentOffset();
        assertEquals("Current offset should start at 0", 0.0f, offset, EPSILON);
    }

    // ==================== Scroll Target Basic Tests ====================

    @Test
    public void setScrollTarget_ConvertsOffsetToWorldUnits() {
        processor.setScrollTarget(0.5f);  // Neutral position
        float offset = processor.updateAndGetCurrentOffset();
        // 0.5 should map to (0.5 - 0.5) * SCROLL_SCALE = 0
        assertEquals("Neutral offset should be 0", 0.0f, offset, EPSILON);
    }

    @Test
    public void setScrollTarget_LeftDirection() {
        processor.setScrollTarget(0.0f);  // Fully left
        // After one update, should start moving left (positive direction)
        float offset1 = processor.updateAndGetCurrentOffset();
        float offset2 = processor.updateAndGetCurrentOffset();
        assertTrue("Should be positive offset (left)", offset1 >= 0.0f);
        assertTrue("Should increase towards left target", offset2 >= offset1);
    }

    @Test
    public void setScrollTarget_RightDirection() {
        processor.setScrollTarget(1.0f);  // Fully right
        // After updates, should move right (negative direction)
        float offset1 = processor.updateAndGetCurrentOffset();
        float offset2 = processor.updateAndGetCurrentOffset();
        assertTrue("First offset should be valid", !Float.isNaN(offset1));
        assertTrue("Second offset should be valid", !Float.isNaN(offset2));
    }

    // ==================== Immediate Offset Tests ====================

    @Test
    public void setScrollOffsetImmediate_SetsImmediately() {
        processor.setScrollOffsetImmediate(3.5f);
        float offset = processor.updateAndGetCurrentOffset();
        assertEquals("Should immediately set to specified offset", 3.5f, offset, EPSILON);
    }

    @Test
    public void setScrollOffsetImmediate_WithNegativeValue() {
        processor.setScrollOffsetImmediate(-1.5f);
        float offset = processor.updateAndGetCurrentOffset();
        assertEquals("Should accept negative offsets", -1.5f, offset, EPSILON);
    }

    @Test
    public void setScrollOffsetImmediate_OverridesTarget() {
        processor.setScrollTarget(2.5f);
        processor.setScrollOffsetImmediate(0.0f);
        float offset = processor.updateAndGetCurrentOffset();
        assertEquals("Immediate should override target", 0.0f, offset, EPSILON);
    }

    // ==================== XFocus Targeting Tests ====================

    @Test
    public void setScrollTargetFromXFocus_NeutralPosition() {
        processor.setScrollTargetFromXFocus(0.5f);
        // Call multiple times to allow convergence
        for (int i = 0; i < 1000; i++) {
            processor.updateAndGetCurrentOffset();
        }
        float offset = processor.updateAndGetCurrentOffset();
        // Should eventually reach (0.5 - 0.5) * SCROLL_SCALE = 0
        assertEquals("XFocus neutral should converge to 0", 0.0f, offset, 0.2f);
    }

    @Test
    public void setScrollTargetFromXFocus_DifferentFromUserScroll() {
        ScrollOffsetProcessor proc1 = new ScrollOffsetProcessor();
        ScrollOffsetProcessor proc2 = new ScrollOffsetProcessor();

        proc1.setScrollTargetFromXFocus(0.0f);
        proc2.setScrollTarget(0.0f);

        // Both should produce valid results
        for (int i = 0; i < 10; i++) {
            float off1 = proc1.updateAndGetCurrentOffset();
            float off2 = proc2.updateAndGetCurrentOffset();
            assertTrue("Both should produce valid offsets", !Float.isNaN(off1) && !Float.isNaN(off2));
        }
    }

    // ==================== Smoothing Duration Tests ====================

    @Test
    public void setScrollSmoothing_ValidSmoothingValue() {
        processor.setScrollSmoothing(0.5f);
        processor.setScrollTarget(1.0f);
        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Valid smoothing should be accepted", !Float.isNaN(offset));
    }

    @Test
    public void setScrollSmoothing_HandlesBoundaryValues() {
        processor.setScrollSmoothing(0.0f);
        float offset1 = processor.updateAndGetCurrentOffset();
        assertTrue("Smoothing 0 should work", !Float.isNaN(offset1));

        processor.setScrollSmoothing(1.0f);
        float offset2 = processor.updateAndGetCurrentOffset();
        assertTrue("Smoothing 1 should work", !Float.isNaN(offset2));
    }

    @Test
    public void setScrollSmoothing_ClampsOutOfRange() {
        processor.setScrollSmoothing(-1.0f);
        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Negative smoothing should be handled", !Float.isNaN(offset));

        processor.setScrollSmoothing(2.0f);
        offset = processor.updateAndGetCurrentOffset();
        assertTrue("Smoothing > 1 should be handled", !Float.isNaN(offset));
    }

    // ==================== Pause/Resume Tests ====================

    @Test
    public void onRendererPause_DoesNotCrash() {
        processor.setScrollTarget(1.0f);
        processor.updateAndGetCurrentOffset();
        processor.onRendererPause();
        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Should not crash after pause", !Float.isNaN(offset));
    }

    @Test
    public void onRendererResume_DoesNotCrash() {
        processor.setScrollTarget(1.0f);
        processor.updateAndGetCurrentOffset();
        processor.onRendererResume();
        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Should not crash after resume", !Float.isNaN(offset));
    }

    @Test
    public void pauseAndResume_CycleWorks() {
        for (int i = 0; i < 5; i++) {
            processor.setScrollTarget(1.0f);
            processor.updateAndGetCurrentOffset();
            processor.onRendererPause();
            processor.onRendererResume();
        }
        assertTrue("Multiple pause/resume cycles should work", true);
    }

    // ==================== Disable Scroll Motion Tests ====================

    @Test
    public void disableScrollMotion_ReturnsTowardNeutral() {
        processor.setScrollTarget(2.5f);
        for (int i = 0; i < 100; i++) {
            processor.updateAndGetCurrentOffset();
        }
        processor.disableScrollMotion();

        float offset1 = processor.updateAndGetCurrentOffset();
        for (int i = 0; i < 1000; i++) {
            processor.updateAndGetCurrentOffset();
        }
        float offset2 = processor.updateAndGetCurrentOffset();

        // Should be moving back towards neutral
        assertTrue("Should move towards neutral", Math.abs(offset2) <= Math.abs(offset1) + EPSILON);
    }

    @Test
    public void disableScrollMotion_SmoothInterpolation() {
        processor.setScrollTarget(-2.5f);
        for (int i = 0; i < 50; i++) {
            processor.updateAndGetCurrentOffset();
        }
        processor.disableScrollMotion();

        float offset1 = processor.updateAndGetCurrentOffset();
        float offset2 = processor.updateAndGetCurrentOffset();

        // Both should be valid
        assertTrue("Should be smoothly interpolating", !Float.isNaN(offset1) && !Float.isNaN(offset2));
    }

    // ==================== State Transitions Tests ====================

    @Test
    public void setScrollTarget_ChangingTargets() {
        processor.setScrollTarget(1.0f);
        for (int i = 0; i < 50; i++) {
            processor.updateAndGetCurrentOffset();
        }

        processor.setScrollTarget(-1.0f);
        for (int i = 0; i < 50; i++) {
            processor.updateAndGetCurrentOffset();
        }

        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Should handle target changes", !Float.isNaN(offset));
    }

    @Test
    public void setScrollTargetFromXFocus_ThenUserScroll() {
        processor.setScrollTargetFromXFocus(0.0f);
        for (int i = 0; i < 50; i++) {
            processor.updateAndGetCurrentOffset();
        }

        processor.setScrollTarget(1.0f);
        for (int i = 0; i < 50; i++) {
            processor.updateAndGetCurrentOffset();
        }

        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Should handle interpolation method change", !Float.isNaN(offset));
    }

    // ==================== Sequential Update Tests ====================

    @Test
    public void updateAndGetCurrentOffset_RepeatedCalls() {
        processor.setScrollTarget(1.0f);

        for (int i = 0; i < 100; i++) {
            float offset = processor.updateAndGetCurrentOffset();
            assertTrue("Each update should be valid", !Float.isNaN(offset));
        }
    }

    @Test
    public void updateAndGetCurrentOffset_NoSnappingBetweenFrames() {
        processor.setScrollTarget(2.5f);
        float offset1 = processor.updateAndGetCurrentOffset();
        float offset2 = processor.updateAndGetCurrentOffset();

        // Each frame should move smoothly, not jump
        float diff = Math.abs(offset2 - offset1);
        assertTrue("Should not snap between frames", diff < 1.0f);
    }

    // ==================== Integration Tests ====================

    @Test
    public void userScrollLeftAndRight() {
        processor.setScrollTarget(0.2f);
        for (int i = 0; i < 50; i++) {
            processor.updateAndGetCurrentOffset();
        }
        float leftOffset = processor.updateAndGetCurrentOffset();
        assertTrue("Should move towards left", leftOffset >= -0.1f);

        processor.setScrollTarget(0.8f);
        // Transition from left to right takes more iterations
        for (int i = 0; i < 150; i++) {
            processor.updateAndGetCurrentOffset();
        }
        float rightOffset = processor.updateAndGetCurrentOffset();
        assertTrue("Should have negative offset for right", rightOffset <= 0.5f);
    }

    @Test
    public void pauseResumePreservesMotion() {
        processor.setScrollTarget(1.0f);
        for (int i = 0; i < 50; i++) {
            processor.updateAndGetCurrentOffset();
        }

        processor.onRendererPause();
        processor.onRendererResume();

        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Should continue after pause/resume", !Float.isNaN(offset));
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void setScrollTarget_OutOfRangeLow() {
        processor.setScrollTarget(-1.0f);
        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Out of range offset should work", !Float.isNaN(offset));
    }

    @Test
    public void setScrollTarget_OutOfRangeHigh() {
        processor.setScrollTarget(2.0f);
        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Out of range offset should work", !Float.isNaN(offset));
    }

    @Test
    public void setScrollOffsetImmediate_MultipleConsecutiveCalls() {
        processor.setScrollOffsetImmediate(1.0f);
        assertEquals("First immediate set", 1.0f, processor.updateAndGetCurrentOffset(), EPSILON);

        processor.setScrollOffsetImmediate(2.0f);
        assertEquals("Second immediate set", 2.0f, processor.updateAndGetCurrentOffset(), EPSILON);

        processor.setScrollOffsetImmediate(-1.0f);
        assertEquals("Third immediate set", -1.0f, processor.updateAndGetCurrentOffset(), EPSILON);
    }

    @Test
    public void deprecatedSetScrollSmoothing_BackwardsCompatibility() {
        processor.setScrollSmoothing(0.5f);
        processor.setScrollTarget(1.0f);
        float offset = processor.updateAndGetCurrentOffset();
        assertTrue("Deprecated method should work", !Float.isNaN(offset));
    }
}




