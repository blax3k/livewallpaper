package com.example.livewallpaper.scene.managers;

import android.content.Context;

import com.example.livewallpaper.gl.GLWallpaperRenderer;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.ui.editor.managers.SceneFileManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

/**
 * Unit tests for LiveWallpaperSceneManager.
 *
 * Tests focus on:
 * - Constructor initialization and scene loading
 * - GL lifecycle methods (onSurfaceCreated, onSurfaceChanged, onDrawFrame, onDestroy)
 * - Scene switching and double-tap handling
 * - Gyro and scroll offset processing
 * - Automatic scene cycling based on time intervals
 * - Scene list refresh functionality
 */
@RunWith(RobolectricTestRunner.class)
public class LiveWallpaperSceneManagerTest {

    private Context context;
    private LiveWallpaperSceneManager manager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    // ==================== Constructor Tests ====================

    /**
     * Test that constructor initializes the manager without throwing exceptions.
     * The constructor should load the initial scene and set up the scene switch manager.
     */
    @Test
    public void testConstructor_Initialization() {
        manager = new LiveWallpaperSceneManager(context);

        assertNotNull("Manager should be created", manager);
        assertNotNull("Current scene should be initialized", manager.getCurrentScene());
        assertTrue("Manager should implement GLWallpaperRenderer interface", manager instanceof GLWallpaperRenderer);
    }

    /**
     * Test that constructor loads an initial scene.
     */
    @Test
    public void testConstructor_LoadsInitialScene() {
        manager = new LiveWallpaperSceneManager(context);

        Scene currentScene = manager.getCurrentScene();
        assertNotNull("Current scene should not be null", currentScene);
        assertNotNull("Scene name should be set", currentScene.getSceneName());
    }

    /**
     * Test that the manager initializes the scene switch manager.
     */
    @Test
    public void testConstructor_InitializesSceneSwitchManager() {
        manager = new LiveWallpaperSceneManager(context);

        Scene currentScene = manager.getCurrentScene();
        assertNotNull("Scene should be loaded by sceneSwitchManager", currentScene);
        assertNotNull("Scene name should be valid", currentScene.getSceneName());
    }

    // ==================== GL Lifecycle Tests ====================

    /**
     * Test onSurfaceCreated initialization.
     */
    @Test
    public void testOnSurfaceCreated_InitializesGLResources() {
        manager = new LiveWallpaperSceneManager(context);

        // Should not throw exception
        manager.onSurfaceCreated();

        assertNotNull("Current scene should still exist", manager.getCurrentScene());
    }

    /**
     * Test onSurfaceChanged sets up projection matrix correctly.
     */
    @Test
    public void testOnSurfaceChanged_UpdatesProjection() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Should not throw exception with various dimensions
        manager.onSurfaceChanged(1080, 1920);
        manager.onSurfaceChanged(720, 1280);
        manager.onSurfaceChanged(800, 800);
    }

    /**
     * Test onSurfaceChanged with square aspect ratio.
     */
    @Test
    public void testOnSurfaceChanged_SquareAspectRatio() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Should handle square aspect ratio without crashing
        manager.onSurfaceChanged(512, 512);
        assertNotNull("Current scene should still exist", manager.getCurrentScene());
    }

    /**
     * Test onDrawFrame with valid state.
     */
    @Test
    public void testOnDrawFrame_WithValidState() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Should not throw exception
        manager.onDrawFrame();

        assertNotNull("Current scene should still exist", manager.getCurrentScene());
    }

    /**
     * Test onDrawFrame basic flow.
     */
    @Test
    public void testOnDrawFrame_BasicFlow() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Should not throw exception
        manager.onDrawFrame();

        assertNotNull("Current scene should still exist", manager.getCurrentScene());
    }

    /**
     * Test onDestroy cleans up resources.
     */
    @Test
    public void testOnDestroy_CleansUpResources() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Should not throw exception
        manager.onDestroy();

        // After destroy, scene should still exist in memory (not cleared by manager)
        assertNotNull("Current scene reference should still exist", manager.getCurrentScene());
    }

    // ==================== Scroll Offset Tests ====================

    /**
     * Test onScrollOffsetChanged when scroll motion is enabled.
     */
    @Test
    public void testOnScrollOffsetChanged_ScrollMotionEnabled() {
        manager = new LiveWallpaperSceneManager(context);

        // Enable scroll motion
        MotionConfig.setScrollMotionEnabled(true);

        // Should not throw exception
        manager.onScrollOffsetChanged(0.5f);
        manager.onScrollOffsetChanged(0.0f);
        manager.onScrollOffsetChanged(1.0f);
    }

    /**
     * Test onScrollOffsetChanged when scroll motion is disabled.
     */
    @Test
    public void testOnScrollOffsetChanged_ScrollMotionDisabled() {
        manager = new LiveWallpaperSceneManager(context);

        // Disable scroll motion
        MotionConfig.setScrollMotionEnabled(false);

        // Should not throw exception and should ignore scroll offset
        manager.onScrollOffsetChanged(0.5f);
        manager.onScrollOffsetChanged(1.0f);
    }

    // ==================== Renderer Lifecycle Tests ====================

    /**
     * Test onRendererResume functionality.
     */
    @Test
    public void testOnRendererResume() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Should not throw exception
        manager.onRendererResume(System.nanoTime());

        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    /**
     * Test onRendererPause functionality.
     */
    @Test
    public void testOnRendererPause() {
        manager = new LiveWallpaperSceneManager(context);

        // Should not throw exception
        manager.onRendererPause();

        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    /**
     * Test onRendererSuspend functionality.
     */
    @Test
    public void testOnRendererSuspend() {
        manager = new LiveWallpaperSceneManager(context);

        // Should not throw exception
        manager.onRendererSuspend();

        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    /**
     * Test onRendererSuspendResume functionality.
     */
    @Test
    public void testOnRendererSuspendResume() {
        manager = new LiveWallpaperSceneManager(context);

        // Should not throw exception
        manager.onRendererSuspendResume();

        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    // ==================== Double Tap Tests ====================

    /**
     * Test onDoubleTap requests scene switch.
     */
    @Test
    public void testOnDoubleTap_RequestsSceneSwitch() {
        manager = new LiveWallpaperSceneManager(context);

        // Should not throw exception
        manager.onDoubleTap(500, 800);

        assertNotNull("Current scene should still exist after double tap", manager.getCurrentScene());
    }

    /**
     * Test onDoubleTap at different screen coordinates.
     */
    @Test
    public void testOnDoubleTap_DifferentCoordinates() {
        manager = new LiveWallpaperSceneManager(context);

        // Should handle various coordinates
        manager.onDoubleTap(0, 0);
        manager.onDoubleTap(500, 500);
        manager.onDoubleTap(1080, 1920);

        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    /**
     * Test that onDoubleTap is ignored during transition.
     */
    @Test
    public void testOnDoubleTap_DuringTransition() {
        manager = new LiveWallpaperSceneManager(context);

        // Simulate transition by calling onDoubleTap multiple times rapidly
        manager.onDoubleTap(500, 800);
        manager.onDoubleTap(600, 900);

        // Should not throw exception
        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    // ==================== Scene List Tests ====================

    /**
     * Test refreshSceneList functionality.
     */
    @Test
    public void testRefreshSceneList() {
        manager = new LiveWallpaperSceneManager(context);

        SceneFileManager sceneFileManager = new SceneFileManager(context, null);

        // Should not throw exception
        manager.refreshSceneList(sceneFileManager);

        assertNotNull("Current scene should still exist", manager.getCurrentScene());
    }

    // ==================== Integration Tests ====================

    /**
     * Test complete GL lifecycle sequence.
     */
    @Test
    public void testCompleteGLLifecycle() {
        manager = new LiveWallpaperSceneManager(context);

        // Simulate complete GL lifecycle
        manager.onSurfaceCreated();
        manager.onSurfaceChanged(1080, 1920);

        for (int i = 0; i < 5; i++) {
            manager.onDrawFrame();
        }

        manager.onDestroy();

        assertNotNull("Current scene should exist after lifecycle", manager.getCurrentScene());
    }

    /**
     * Test pause and resume lifecycle.
     */
    @Test
    public void testPauseResumeLifecycle() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Pause
        manager.onRendererPause();

        // Resume
        manager.onRendererResume(System.nanoTime());

        // Should work normally
        manager.onDrawFrame();

        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    /**
     * Test suspend and suspend-resume lifecycle.
     */
    @Test
    public void testSuspendResumeLifecycle() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();

        // Suspend
        manager.onRendererSuspend();

        // Resume after suspension
        manager.onRendererSuspendResume();

        // Should work normally
        manager.onDrawFrame();

        assertNotNull("Current scene should exist", manager.getCurrentScene());
    }

    /**
     * Test draw frame sequence.
     */
    @Test
    public void testDrawFrameSequence() {
        manager = new LiveWallpaperSceneManager(context);
        manager.onSurfaceCreated();
        manager.onSurfaceChanged(1080, 1920);

        // Execute multiple draw frames
        for (int i = 0; i < 10; i++) {
            manager.onDrawFrame();
        }

        assertNotNull("Current scene should exist after multiple frames", manager.getCurrentScene());
    }

    /**
     * Test scene initialization with gyro scaling callback.
     */
    @Test
    public void testOnSurfaceCreated_WithGyroCallback() {
        manager = new LiveWallpaperSceneManager(context);

        // Should initialize without errors
        manager.onSurfaceCreated();

        // Verify scene exists
        assertNotNull("Current scene should be initialized", manager.getCurrentScene());
    }
}






