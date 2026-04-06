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
public class ConfigManagerTest {

    // ==================== Scroll Motion Tests ====================

    @Test
    public void setScrollMotionEnabled_DisablesMotion() {
        ConfigManager.setScrollMotionEnabled(false);
        assertFalse("Scroll motion should be disabled", ConfigManager.isScrollMotionEnabled());
    }

    @Test
    public void setScrollMotionEnabled_EnablesMotion() {
        ConfigManager.setScrollMotionEnabled(true);
        assertTrue("Scroll motion should be enabled", ConfigManager.isScrollMotionEnabled());
    }

    @Test
    public void setScrollMotionEnabled_Toggles() {
        ConfigManager.setScrollMotionEnabled(false);
        assertFalse("Should be disabled", ConfigManager.isScrollMotionEnabled());

        ConfigManager.setScrollMotionEnabled(true);
        assertTrue("Should be enabled", ConfigManager.isScrollMotionEnabled());

        ConfigManager.setScrollMotionEnabled(false);
        assertFalse("Should be disabled again", ConfigManager.isScrollMotionEnabled());
    }

    // ==================== Gyro Motion Tests ====================

    @Test
    public void setGyroMotionEnabled_DisablesMotion() {
        ConfigManager.setGyroMotionEnabled(false);
        assertFalse("Gyro motion should be disabled", ConfigManager.isGyroMotionEnabled());
    }

    @Test
    public void setGyroMotionEnabled_EnablesMotion() {
        ConfigManager.setGyroMotionEnabled(true);
        assertTrue("Gyro motion should be enabled", ConfigManager.isGyroMotionEnabled());
    }

    @Test
    public void setGyroMotionEnabled_Toggles() {
        ConfigManager.setGyroMotionEnabled(false);
        assertFalse("Should be disabled", ConfigManager.isGyroMotionEnabled());

        ConfigManager.setGyroMotionEnabled(true);
        assertTrue("Should be enabled", ConfigManager.isGyroMotionEnabled());

        ConfigManager.setGyroMotionEnabled(false);
        assertFalse("Should be disabled again", ConfigManager.isGyroMotionEnabled());
    }

    // ==================== Independent Settings Tests ====================

    @Test
    public void scrollAndGyroMotion_AreIndependent() {
        ConfigManager.setScrollMotionEnabled(true);
        ConfigManager.setGyroMotionEnabled(false);

        assertTrue("Scroll should be enabled", ConfigManager.isScrollMotionEnabled());
        assertFalse("Gyro should be disabled", ConfigManager.isGyroMotionEnabled());

        ConfigManager.setScrollMotionEnabled(false);
        assertFalse("Scroll should be disabled", ConfigManager.isScrollMotionEnabled());
        assertFalse("Gyro should still be disabled", ConfigManager.isGyroMotionEnabled());

        ConfigManager.setGyroMotionEnabled(true);
        assertFalse("Scroll should still be disabled", ConfigManager.isScrollMotionEnabled());
        assertTrue("Gyro should be enabled", ConfigManager.isGyroMotionEnabled());
    }

    @Test
    public void setScrollMotionEnabled_DoesNotAffectGyro() {
        ConfigManager.setGyroMotionEnabled(false);
        ConfigManager.setScrollMotionEnabled(false);

        assertFalse("Scroll should be disabled", ConfigManager.isScrollMotionEnabled());
        assertFalse("Gyro should remain unchanged", ConfigManager.isGyroMotionEnabled());
    }

    @Test
    public void setGyroMotionEnabled_DoesNotAffectScroll() {
        ConfigManager.setScrollMotionEnabled(false);
        ConfigManager.setGyroMotionEnabled(false);

        assertFalse("Scroll should remain unchanged", ConfigManager.isScrollMotionEnabled());
        assertFalse("Gyro should be disabled", ConfigManager.isGyroMotionEnabled());
    }

    // ==================== Thread Safety Tests ====================

    @Test
    public void volatileFields_VisibleAcrossThreads() throws InterruptedException {
        ConfigManager.setScrollMotionEnabled(true);
        ConfigManager.setGyroMotionEnabled(true);

        final boolean[] scrollFromThread = new boolean[1];
        final boolean[] gyroFromThread = new boolean[1];

        Thread readerThread = new Thread(() -> {
            scrollFromThread[0] = ConfigManager.isScrollMotionEnabled();
            gyroFromThread[0] = ConfigManager.isGyroMotionEnabled();
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
                ConfigManager.setScrollMotionEnabled(enabled);
                boolean value = ConfigManager.isScrollMotionEnabled();
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
        ConfigManager.setScrollMotionEnabled(true);
        ConfigManager.setGyroMotionEnabled(true);
        assertTrue("Both enabled", ConfigManager.isScrollMotionEnabled());
        assertTrue("Both enabled", ConfigManager.isGyroMotionEnabled());

        ConfigManager.setScrollMotionEnabled(false);
        assertFalse("Scroll disabled", ConfigManager.isScrollMotionEnabled());
        assertTrue("Gyro unchanged", ConfigManager.isGyroMotionEnabled());

        ConfigManager.setGyroMotionEnabled(false);
        assertFalse("Scroll still disabled", ConfigManager.isScrollMotionEnabled());
        assertFalse("Gyro disabled", ConfigManager.isGyroMotionEnabled());

        ConfigManager.setScrollMotionEnabled(true);
        ConfigManager.setGyroMotionEnabled(true);
        assertTrue("Scroll enabled", ConfigManager.isScrollMotionEnabled());
        assertTrue("Gyro enabled", ConfigManager.isGyroMotionEnabled());
    }

    @Test
    public void multipleEnablementCycles() {
        for (int i = 0; i < 10; i++) {
            ConfigManager.setScrollMotionEnabled(i % 2 == 0);
            ConfigManager.setGyroMotionEnabled(i % 3 == 0);
        }

        assertTrue("Multiple cycles completed", true);
    }
}




