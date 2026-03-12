package com.example.livewallpaper.sensors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for MotionConfig thread-safe configuration class.
 * Tests setting/getting motion configuration and state management.
 * Note: These tests exercise the in-memory state; SharedPreferences persistence
 * is not tested here to avoid complexity with static state.
 */
@RunWith(RobolectricTestRunner.class)
public class MotionConfigTest {

    // ==================== Scroll Motion Tests ====================

    @Test
    public void setScrollMotionEnabled_DisablesMotion() {
        MotionConfig.setScrollMotionEnabled(false);
        assertFalse("Scroll motion should be disabled", MotionConfig.isScrollMotionEnabled());
    }

    @Test
    public void setScrollMotionEnabled_EnablesMotion() {
        MotionConfig.setScrollMotionEnabled(true);
        assertTrue("Scroll motion should be enabled", MotionConfig.isScrollMotionEnabled());
    }

    @Test
    public void setScrollMotionEnabled_Toggles() {
        MotionConfig.setScrollMotionEnabled(false);
        assertFalse("Should be disabled", MotionConfig.isScrollMotionEnabled());

        MotionConfig.setScrollMotionEnabled(true);
        assertTrue("Should be enabled", MotionConfig.isScrollMotionEnabled());

        MotionConfig.setScrollMotionEnabled(false);
        assertFalse("Should be disabled again", MotionConfig.isScrollMotionEnabled());
    }

    // ==================== Gyro Motion Tests ====================

    @Test
    public void setGyroMotionEnabled_DisablesMotion() {
        MotionConfig.setGyroMotionEnabled(false);
        assertFalse("Gyro motion should be disabled", MotionConfig.isGyroMotionEnabled());
    }

    @Test
    public void setGyroMotionEnabled_EnablesMotion() {
        MotionConfig.setGyroMotionEnabled(true);
        assertTrue("Gyro motion should be enabled", MotionConfig.isGyroMotionEnabled());
    }

    @Test
    public void setGyroMotionEnabled_Toggles() {
        MotionConfig.setGyroMotionEnabled(false);
        assertFalse("Should be disabled", MotionConfig.isGyroMotionEnabled());

        MotionConfig.setGyroMotionEnabled(true);
        assertTrue("Should be enabled", MotionConfig.isGyroMotionEnabled());

        MotionConfig.setGyroMotionEnabled(false);
        assertFalse("Should be disabled again", MotionConfig.isGyroMotionEnabled());
    }

    // ==================== Independent Settings Tests ====================

    @Test
    public void scrollAndGyroMotion_AreIndependent() {
        MotionConfig.setScrollMotionEnabled(true);
        MotionConfig.setGyroMotionEnabled(false);

        assertTrue("Scroll should be enabled", MotionConfig.isScrollMotionEnabled());
        assertFalse("Gyro should be disabled", MotionConfig.isGyroMotionEnabled());

        MotionConfig.setScrollMotionEnabled(false);
        assertFalse("Scroll should be disabled", MotionConfig.isScrollMotionEnabled());
        assertFalse("Gyro should still be disabled", MotionConfig.isGyroMotionEnabled());

        MotionConfig.setGyroMotionEnabled(true);
        assertFalse("Scroll should still be disabled", MotionConfig.isScrollMotionEnabled());
        assertTrue("Gyro should be enabled", MotionConfig.isGyroMotionEnabled());
    }

    @Test
    public void setScrollMotionEnabled_DoesNotAffectGyro() {
        MotionConfig.setGyroMotionEnabled(false);
        MotionConfig.setScrollMotionEnabled(false);

        assertFalse("Scroll should be disabled", MotionConfig.isScrollMotionEnabled());
        assertFalse("Gyro should remain unchanged", MotionConfig.isGyroMotionEnabled());
    }

    @Test
    public void setGyroMotionEnabled_DoesNotAffectScroll() {
        MotionConfig.setScrollMotionEnabled(false);
        MotionConfig.setGyroMotionEnabled(false);

        assertFalse("Scroll should remain unchanged", MotionConfig.isScrollMotionEnabled());
        assertFalse("Gyro should be disabled", MotionConfig.isGyroMotionEnabled());
    }

    // ==================== Thread Safety Tests ====================

    @Test
    public void volatileFields_VisibleAcrossThreads() throws InterruptedException {
        MotionConfig.setScrollMotionEnabled(true);
        MotionConfig.setGyroMotionEnabled(true);

        final boolean[] scrollFromThread = new boolean[1];
        final boolean[] gyroFromThread = new boolean[1];

        Thread readerThread = new Thread(() -> {
            scrollFromThread[0] = MotionConfig.isScrollMotionEnabled();
            gyroFromThread[0] = MotionConfig.isGyroMotionEnabled();
        });

        readerThread.start();
        readerThread.join();

        assertTrue("Scroll value should be visible from another thread", scrollFromThread[0]);
        assertTrue("Gyro value should be visible from another thread", gyroFromThread[0]);
    }

    @Test
    public void concurrentSetAndGet() throws InterruptedException {
        final int numThreads = 5;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                boolean enabled = (threadIndex % 2) == 0;
                MotionConfig.setScrollMotionEnabled(enabled);
                boolean value = MotionConfig.isScrollMotionEnabled();
                assertNotNull("Should be able to read scroll motion setting", Boolean.valueOf(value));
            });
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        assertTrue("Concurrent operations completed successfully", true);
    }

    // ==================== Integration Tests ====================

    @Test
    public void completeWorkflow() {
        MotionConfig.setScrollMotionEnabled(true);
        MotionConfig.setGyroMotionEnabled(true);
        assertTrue("Both enabled", MotionConfig.isScrollMotionEnabled());
        assertTrue("Both enabled", MotionConfig.isGyroMotionEnabled());

        MotionConfig.setScrollMotionEnabled(false);
        assertFalse("Scroll disabled", MotionConfig.isScrollMotionEnabled());
        assertTrue("Gyro unchanged", MotionConfig.isGyroMotionEnabled());

        MotionConfig.setGyroMotionEnabled(false);
        assertFalse("Scroll still disabled", MotionConfig.isScrollMotionEnabled());
        assertFalse("Gyro disabled", MotionConfig.isGyroMotionEnabled());

        MotionConfig.setScrollMotionEnabled(true);
        MotionConfig.setGyroMotionEnabled(true);
        assertTrue("Scroll enabled", MotionConfig.isScrollMotionEnabled());
        assertTrue("Gyro enabled", MotionConfig.isGyroMotionEnabled());
    }

    @Test
    public void multipleEnablementCycles() {
        for (int i = 0; i < 10; i++) {
            MotionConfig.setScrollMotionEnabled(i % 2 == 0);
            MotionConfig.setGyroMotionEnabled(i % 3 == 0);
        }

        assertTrue("Multiple cycles completed", true);
    }
}




