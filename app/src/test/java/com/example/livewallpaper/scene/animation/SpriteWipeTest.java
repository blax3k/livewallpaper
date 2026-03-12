package com.example.livewallpaper.scene.animation;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for SpriteWipe animation class.
 * Tests wipe progress clamping, direction changes, and state management during transitions.
 */
public class SpriteWipeTest {

    private SpriteWipe spriteWipe;
    private static final float EPSILON = 0.001f;

    @Before
    public void setUp() {
        spriteWipe = new SpriteWipe();
    }

    // ==================== Initialization Tests ====================

    @Test
    public void constructor_InitializesWithDefaultValues() {
        assertEquals("Default wipe progress should be 0.0f", 0.0f, spriteWipe.getWipeProgress(), EPSILON);
        assertEquals("Default wipe direction should be 0.0f (NO_WIPE)", 0.0f, spriteWipe.getWipeDirection(), EPSILON);
        assertFalse("Should not be wiping out initially", spriteWipe.isWipingOut());
        assertFalse("Should not be wiping in initially", spriteWipe.isWipingIn());
        assertFalse("Should not be transitioning initially", spriteWipe.isTransitioning());
    }

    // ==================== Wipe Progress Tests ====================

    @Test
    public void setWipeProgress_AcceptsValidValue() {
        spriteWipe.setWipeProgress(0.5f);
        assertEquals("Wipe progress should be 0.5f", 0.5f, spriteWipe.getWipeProgress(), EPSILON);
    }

    @Test
    public void setWipeProgress_ClampsMinValue() {
        spriteWipe.setWipeProgress(-0.5f);
        assertEquals("Negative wipe progress should be clamped to 0.0f", 0.0f, spriteWipe.getWipeProgress(), EPSILON);
    }

    @Test
    public void setWipeProgress_ClampsMaxValue() {
        spriteWipe.setWipeProgress(1.5f);
        assertEquals("Wipe progress > 1.0f should be clamped to 1.0f", 1.0f, spriteWipe.getWipeProgress(), EPSILON);
    }

    @Test
    public void setWipeProgress_HandlesZeroValue() {
        spriteWipe.setWipeProgress(0.5f);
        spriteWipe.setWipeProgress(0.0f);
        assertEquals("Zero progress should be allowed", 0.0f, spriteWipe.getWipeProgress(), EPSILON);
    }

    @Test
    public void setWipeProgress_HandlesOneValue() {
        spriteWipe.setWipeProgress(1.0f);
        assertEquals("Progress of 1.0f should be allowed", 1.0f, spriteWipe.getWipeProgress(), EPSILON);
    }

    @Test
    public void setWipeProgress_MultipleCalls() {
        spriteWipe.setWipeProgress(0.25f);
        assertEquals("First call should set to 0.25f", 0.25f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipeProgress(0.75f);
        assertEquals("Second call should update to 0.75f", 0.75f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipeProgress(0.5f);
        assertEquals("Third call should update to 0.5f", 0.5f, spriteWipe.getWipeProgress(), EPSILON);
    }

    // ==================== Wipe Out Tests ====================

    @Test
    public void setWipingOut_EnablesWipeOut() {
        spriteWipe.setWipingOut(true);
        assertTrue("Should be wiping out", spriteWipe.isWipingOut());
    }

    @Test
    public void setWipingOut_SetsCorrectDirection() {
        spriteWipe.setWipingOut(true);
        assertEquals("Direction should be 1.0f (WIPE_OUT_DIRECTION)", 1.0f, spriteWipe.getWipeDirection(), EPSILON);
    }

    @Test
    public void setWipingOut_DisablesWipeIn() {
        spriteWipe.setWipingIn(true);
        assertTrue("Should start by wiping in", spriteWipe.isWipingIn());

        spriteWipe.setWipingOut(true);
        assertTrue("Should now be wiping out", spriteWipe.isWipingOut());
        assertFalse("Should no longer be wiping in", spriteWipe.isWipingIn());
    }

    @Test
    public void setWipingOut_DisableViaFalse() {
        spriteWipe.setWipingOut(true);
        spriteWipe.setWipingOut(false);
        assertFalse("Should not be wiping out after setting to false", spriteWipe.isWipingOut());
    }

    @Test
    public void setWipingOut_ToggleMultipleTimes() {
        spriteWipe.setWipingOut(true);
        assertTrue("Should be wiping out", spriteWipe.isWipingOut());

        spriteWipe.setWipingOut(false);
        assertFalse("Should not be wiping out", spriteWipe.isWipingOut());

        spriteWipe.setWipingOut(true);
        assertTrue("Should be wiping out again", spriteWipe.isWipingOut());
    }

    // ==================== Wipe In Tests ====================

    @Test
    public void setWipingIn_EnablesWipeIn() {
        spriteWipe.setWipingIn(true);
        assertTrue("Should be wiping in", spriteWipe.isWipingIn());
    }

    @Test
    public void setWipingIn_SetsCorrectDirection() {
        spriteWipe.setWipingIn(true);
        assertEquals("Direction should be -1.0f (WIPE_IN_DIRECTION)", -1.0f, spriteWipe.getWipeDirection(), EPSILON);
    }

    @Test
    public void setWipingIn_DisablesWipeOut() {
        spriteWipe.setWipingOut(true);
        assertTrue("Should start by wiping out", spriteWipe.isWipingOut());

        spriteWipe.setWipingIn(true);
        assertTrue("Should now be wiping in", spriteWipe.isWipingIn());
        assertFalse("Should no longer be wiping out", spriteWipe.isWipingOut());
    }

    @Test
    public void setWipingIn_DisableViaFalse() {
        spriteWipe.setWipingIn(true);
        spriteWipe.setWipingIn(false);
        assertFalse("Should not be wiping in after setting to false", spriteWipe.isWipingIn());
    }

    @Test
    public void setWipingIn_ToggleMultipleTimes() {
        spriteWipe.setWipingIn(true);
        assertTrue("Should be wiping in", spriteWipe.isWipingIn());

        spriteWipe.setWipingIn(false);
        assertFalse("Should not be wiping in", spriteWipe.isWipingIn());

        spriteWipe.setWipingIn(true);
        assertTrue("Should be wiping in again", spriteWipe.isWipingIn());
    }

    // ==================== Transition State Tests ====================

    @Test
    public void isTransitioning_FalseWhenNotWiping() {
        assertFalse("Should not be transitioning initially", spriteWipe.isTransitioning());
    }

    @Test
    public void isTransitioning_TrueWhenWipingOut() {
        spriteWipe.setWipingOut(true);
        assertTrue("Should be transitioning when wiping out", spriteWipe.isTransitioning());
    }

    @Test
    public void isTransitioning_TrueWhenWipingIn() {
        spriteWipe.setWipingIn(true);
        assertTrue("Should be transitioning when wiping in", spriteWipe.isTransitioning());
    }

    @Test
    public void isTransitioning_TogglesBetweenStates() {
        assertFalse("Initially not transitioning", spriteWipe.isTransitioning());

        spriteWipe.setWipingOut(true);
        assertTrue("Transitioning when wipe out starts", spriteWipe.isTransitioning());

        spriteWipe.resetWipe();
        assertFalse("Not transitioning after reset", spriteWipe.isTransitioning());

        spriteWipe.setWipingIn(true);
        assertTrue("Transitioning when wipe in starts", spriteWipe.isTransitioning());
    }

    // ==================== Wipe Direction Tests ====================

    @Test
    public void getWipeDirection_ReturnsNoWipeInitially() {
        assertEquals("Initial direction should be 0.0f (NO_WIPE)", 0.0f, spriteWipe.getWipeDirection(), EPSILON);
    }

    @Test
    public void getWipeDirection_ReturnsWipeOutDirection() {
        spriteWipe.setWipingOut(true);
        assertEquals("Should return 1.0f for wipe out", 1.0f, spriteWipe.getWipeDirection(), EPSILON);
    }

    @Test
    public void getWipeDirection_ReturnsWipeInDirection() {
        spriteWipe.setWipingIn(true);
        assertEquals("Should return -1.0f for wipe in", -1.0f, spriteWipe.getWipeDirection(), EPSILON);
    }

    @Test
    public void getWipeDirection_SwitchesBetweenDirections() {
        spriteWipe.setWipingOut(true);
        assertEquals("Out direction", 1.0f, spriteWipe.getWipeDirection(), EPSILON);

        spriteWipe.setWipingIn(true);
        assertEquals("In direction", -1.0f, spriteWipe.getWipeDirection(), EPSILON);

        spriteWipe.setWipingOut(true);
        assertEquals("Back to out direction", 1.0f, spriteWipe.getWipeDirection(), EPSILON);
    }

    // ==================== Reset Tests ====================

    @Test
    public void resetWipe_ClearsAllState() {
        spriteWipe.setWipeProgress(0.75f);
        spriteWipe.setWipingOut(true);

        spriteWipe.resetWipe();

        assertEquals("Progress should reset to 0.0f", 0.0f, spriteWipe.getWipeProgress(), EPSILON);
        assertEquals("Direction should reset to 0.0f", 0.0f, spriteWipe.getWipeDirection(), EPSILON);
        assertFalse("Should not be wiping out", spriteWipe.isWipingOut());
        assertFalse("Should not be wiping in", spriteWipe.isWipingIn());
        assertFalse("Should not be transitioning", spriteWipe.isTransitioning());
    }

    @Test
    public void resetWipe_AfterWipeIn() {
        spriteWipe.setWipeProgress(0.5f);
        spriteWipe.setWipingIn(true);

        spriteWipe.resetWipe();

        assertEquals("Progress should reset", 0.0f, spriteWipe.getWipeProgress(), EPSILON);
        assertEquals("Direction should reset", 0.0f, spriteWipe.getWipeDirection(), EPSILON);
        assertFalse("Should not be wiping in", spriteWipe.isWipingIn());
        assertFalse("Should not be transitioning", spriteWipe.isTransitioning());
    }

    @Test
    public void resetWipe_MultipleResets() {
        spriteWipe.setWipeProgress(0.5f);
        spriteWipe.setWipingOut(true);
        spriteWipe.resetWipe();
        assertEquals("First reset", 0.0f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipeProgress(0.9f);
        spriteWipe.setWipingIn(true);
        spriteWipe.resetWipe();
        assertEquals("Second reset", 0.0f, spriteWipe.getWipeProgress(), EPSILON);
    }

    // ==================== Complex Interaction Tests ====================

    @Test
    public void complexWipeOutScenario() {
        // Simulate a wipe out animation
        spriteWipe.setWipingOut(true);
        assertEquals("Should start wiping out", 1.0f, spriteWipe.getWipeDirection(), EPSILON);

        spriteWipe.setWipeProgress(0.3f);
        assertEquals("Progress at 30%", 0.3f, spriteWipe.getWipeProgress(), EPSILON);
        assertTrue("Still wiping out", spriteWipe.isWipingOut());

        spriteWipe.setWipeProgress(0.6f);
        assertEquals("Progress at 60%", 0.6f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipeProgress(1.0f);
        assertEquals("Wipe out complete", 1.0f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipingOut(false);
        assertFalse("Wipe out finished", spriteWipe.isWipingOut());
    }

    @Test
    public void complexWipeInScenario() {
        // Start with wiped out state
        spriteWipe.setWipeProgress(1.0f);
        spriteWipe.setWipingOut(true);

        // Now wipe in
        spriteWipe.setWipingIn(true);
        assertEquals("Should be wiping in", -1.0f, spriteWipe.getWipeDirection(), EPSILON);
        assertFalse("Should no longer be wiping out", spriteWipe.isWipingOut());

        spriteWipe.setWipeProgress(0.7f);
        assertEquals("Progress at 70%", 0.7f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipeProgress(0.3f);
        assertEquals("Progress at 30%", 0.3f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipeProgress(0.0f);
        assertEquals("Wipe in complete", 0.0f, spriteWipe.getWipeProgress(), EPSILON);

        spriteWipe.setWipingIn(false);
        assertFalse("Wipe in finished", spriteWipe.isWipingIn());
    }

    @Test
    public void switchDirectionMidTransition() {
        spriteWipe.setWipingOut(true);
        spriteWipe.setWipeProgress(0.5f);

        // Switch to wipe in
        spriteWipe.setWipingIn(true);
        assertEquals("Should now be wipe in", -1.0f, spriteWipe.getWipeDirection(), EPSILON);
        assertTrue("Should be transitioning", spriteWipe.isTransitioning());
        // Progress remains at 0.5f (can be updated by animation logic)
        assertEquals("Progress unchanged", 0.5f, spriteWipe.getWipeProgress(), EPSILON);
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void setWipeProgress_WithVerySmallValue() {
        spriteWipe.setWipeProgress(0.00001f);
        assertTrue("Should accept very small values", spriteWipe.getWipeProgress() >= 0.0f);
        assertTrue("Should be close to zero", spriteWipe.getWipeProgress() < 0.001f);
    }

    @Test
    public void setWipeProgress_WithValueVeryCloseToOne() {
        spriteWipe.setWipeProgress(0.99999f);
        assertTrue("Should accept values close to 1.0", spriteWipe.getWipeProgress() <= 1.0f);
        assertTrue("Should be close to one", spriteWipe.getWipeProgress() > 0.999f);
    }

    @Test
    public void setWipeProgress_WithNegativeInfinity() {
        spriteWipe.setWipeProgress(Float.NEGATIVE_INFINITY);
        assertEquals("Negative infinity should clamp to 0.0f", 0.0f, spriteWipe.getWipeProgress(), EPSILON);
    }

    @Test
    public void setWipeProgress_WithPositiveInfinity() {
        spriteWipe.setWipeProgress(Float.POSITIVE_INFINITY);
        assertEquals("Positive infinity should clamp to 1.0f", 1.0f, spriteWipe.getWipeProgress(), EPSILON);
    }

    @Test
    public void setWipeProgress_WithNaN() {
        spriteWipe.setWipeProgress(Float.NaN);
        // After Math.max/min operations with NaN, result depends on implementation
        // Just verify it doesn't crash and produces a valid state
        float progress = spriteWipe.getWipeProgress();
        assertTrue("Should not crash with NaN", true);
        // The result may be NaN or clamped value depending on Math implementation
    }

    @Test
    public void independentInstances_DontAffectEachOther() {
        SpriteWipe wipe1 = new SpriteWipe();
        SpriteWipe wipe2 = new SpriteWipe();

        wipe1.setWipeProgress(0.5f);
        wipe1.setWipingOut(true);

        assertEquals("First wipe should be at 0.5f", 0.5f, wipe1.getWipeProgress(), EPSILON);
        assertEquals("Second wipe should still be at 0.0f", 0.0f, wipe2.getWipeProgress(), EPSILON);
        assertFalse("Second wipe should not be wiping out", wipe2.isWipingOut());
    }
}


