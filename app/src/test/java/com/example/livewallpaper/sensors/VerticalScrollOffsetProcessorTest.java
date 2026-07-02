package com.example.livewallpaper.sensors;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for VerticalScrollOffsetProcessor, focused on the yFocus offset calculation
 * and the aspect-ratio-driven max offset clamping shared with ScrollOffsetProcessor.
 */
public class VerticalScrollOffsetProcessorTest {

    private VerticalScrollOffsetProcessor processor;
    private static final float EPSILON = 0.1f;

    @Before
    public void setUp() {
        processor = new VerticalScrollOffsetProcessor();
    }

    @Test
    public void constructor_InitializesOffsetToZero() {
        float offset = processor.updateAndGetCurrentOffset();
        assertEquals("Current offset should start at 0", 0.0f, offset, EPSILON);
    }

    @Test
    public void setScrollTargetFromYFocus_NeutralPosition() {
        processor.setScrollTargetFromYFocus(0.5f);
        for (int i = 0; i < 1000; i++) {
            processor.updateAndGetCurrentOffset();
        }
        float offset = processor.updateAndGetCurrentOffset();
        assertEquals("yFocus neutral should converge to 0", 0.0f, offset, EPSILON);
    }

    @Test
    public void setOffsetImmediate_SetsImmediately() {
        processor.setOffsetImmediate(1.5f);
        float offset = processor.updateAndGetCurrentOffset();
        assertEquals("Should immediately set to specified offset", 1.5f, offset, EPSILON);
    }

    // ==================== Max Scroll Offset (Aspect Ratio Clamping) Tests ====================

    /**
     * Drive updateAndGetCurrentOffset() for a fixed number of synthetic 1/60s frames.
     * Forcing a resume before each call keeps dt constant regardless of real wall-clock
     * time elapsed between test statements, so eased convergence is deterministic.
     */
    private float converge(int frames) {
        float offset = 0f;
        for (int i = 0; i < frames; i++) {
            processor.onRendererResume();
            offset = processor.updateAndGetCurrentOffset();
        }
        return offset;
    }

    @Test
    public void setMaxScrollOffset_ZeroLocksOffsetAtCenter() {
        // A square screen has zero slack: no amount of yFocus should move the offset.
        processor.setMaxScrollOffset(0f);
        processor.setScrollTargetFromYFocus(0.0f);  // fully top
        float offset = converge(200);
        assertEquals("Zero max offset should keep the scroll centered", 0.0f, offset, EPSILON);
    }

    @Test
    public void setMaxScrollOffset_ClampsFullyTopTarget() {
        processor.setMaxScrollOffset(1.0f);
        processor.setScrollTargetFromYFocus(0.0f);  // fully top
        float offset = converge(200);
        assertEquals("Offset should converge to the configured max", 1.0f, offset, EPSILON);
    }

    @Test
    public void setMaxScrollOffset_ClampsFullyBottomTarget() {
        processor.setMaxScrollOffset(1.0f);
        processor.setScrollTargetFromYFocus(1.0f);  // fully bottom
        float offset = converge(200);
        assertEquals("Offset should converge to the negative configured max", -1.0f, offset, EPSILON);
    }

    @Test
    public void setMaxScrollOffset_NegativeValueTreatedAsZero() {
        processor.setMaxScrollOffset(-2.0f);
        processor.setScrollTargetFromYFocus(0.0f);
        float offset = converge(200);
        assertEquals("Negative max offset should be clamped to zero", 0.0f, offset, EPSILON);
    }

    @Test
    public void setMaxScrollOffset_DefaultMatchesHistoricalScale() {
        // Default max should reproduce the pre-existing fixed SCROLL_SCALE=5.0 behavior
        // (i.e. yFocus=0.0 converges to +2.5) so unmigrated call sites are unaffected.
        processor.setScrollTargetFromYFocus(0.0f);
        float offset = converge(200);
        assertEquals("Default max offset should match historical behavior", 2.5f, offset, EPSILON);
    }
}
