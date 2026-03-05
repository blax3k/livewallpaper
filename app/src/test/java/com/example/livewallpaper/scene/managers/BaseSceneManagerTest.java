package com.example.livewallpaper.scene.managers;

import android.content.Context;

import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for easily testable methods in BaseSceneManager.
 *
 * Tests focus on:
 * - Simple getters: getCurrentScene(), getSelectedSprite(), getTextureManager(), getAllSprites()
 * - Pure calculations: calculateAspectRatio()
 * - Simple state changes: requestSpriteResort(), updateScrollOffsetFromXFocus()
 * - Index-based selection: selectSpriteByIndex()
 */
@RunWith(RobolectricTestRunner.class)
public class BaseSceneManagerTest {

    private BaseSceneManager sceneManager;
    private Scene testScene;

    /**
     * Create a concrete implementation of BaseSceneManager for testing.
     * Since BaseSceneManager is abstract, we need a test implementation.
     */
    private static class TestSceneManager extends BaseSceneManager {
        public TestSceneManager(Context context, String sceneFileName) {
            super(context, sceneFileName);
        }

        public TestSceneManager(Context context, Scene preloadedScene) {
            super(context, preloadedScene);
        }
    }

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        testScene = new Scene("TestScene");
        sceneManager = new TestSceneManager(context, testScene);
    }

    // ==================== Simple Getter Tests ====================

    /**
     * Test that getCurrentScene() returns the scene provided at construction.
     */
    @Test
    public void testGetCurrentScene_ReturnsSceneFromConstructor() {
        Scene scene = sceneManager.getCurrentScene();
        assertNotNull("Current scene should not be null", scene);
        assertEquals("Should return the same scene passed to constructor", testScene, scene);
    }

    /**
     * Test that getCurrentScene() returns the preloaded scene.
     */
    @Test
    public void testGetCurrentScene_WithPreloadedScene() {
        Scene preloadedScene = new Scene("PreloadedScene");
        Context context = RuntimeEnvironment.getApplication();
        BaseSceneManager manager = new TestSceneManager(context, preloadedScene);

        assertEquals("Should return the preloaded scene", preloadedScene, manager.getCurrentScene());
    }

    /**
     * Test that getSelectedSprite() returns null initially.
     */
    @Test
    public void testGetSelectedSprite_InitiallyNull() {
        Sprite selected = sceneManager.getSelectedSprite();
        assertNull("Selected sprite should be null initially", selected);
    }

    /**
     * Test that getSelectedSprite() returns the sprite after it's set.
     */
    @Test
    public void testGetSelectedSprite_AfterSetting() {
        Sprite sprite = createTestSprite("TestSprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        sceneManager.setSelectedSprite(sprite);

        assertEquals("Should return the sprite that was set", sprite, sceneManager.getSelectedSprite());
    }

    /**
     * Test that getSelectedSprite() returns null after deselection.
     */
    @Test
    public void testGetSelectedSprite_AfterDeselection() {
        Sprite sprite = createTestSprite("TestSprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);
        sceneManager.setSelectedSprite(sprite);

        sceneManager.setSelectedSprite(null);

        assertNull("Selected sprite should be null after deselection", sceneManager.getSelectedSprite());
    }

    /**
     * Test that getTextureManager() returns null initially (before initialization).
     */
    @Test
    public void testGetTextureManager_InitiallyNull() {
        assertNull("TextureManager should be null before initialization", sceneManager.getTextureManager());
    }

    /**
     * Test that getTextureManager() returns the injected texture manager.
     */
    @Test
    public void testGetTextureManager_AfterInitialization() {
        // Manually set the textureManager (in real code this would be initialized by subclass)
        sceneManager.textureManager = new com.example.livewallpaper.gl.TextureManager();

        assertNotNull("TextureManager should not be null", sceneManager.getTextureManager());
    }

    /**
     * Test that getAllSprites() returns an empty list when no sprites are in the scene.
     */
    @Test
    public void testGetAllSprites_EmptyScene() {
        List<Sprite> sprites = sceneManager.getAllSprites();

        assertNotNull("Should return a list, not null", sprites);
        assertTrue("Should return empty list for empty scene", sprites.isEmpty());
    }

    /**
     * Test that getAllSprites() returns all sprites in the scene.
     */
    @Test
    public void testGetAllSprites_WithSprites() {
        Sprite sprite1 = createTestSprite("Sprite1", 1.0f, 1.0f);
        Sprite sprite2 = createTestSprite("Sprite2", 2.0f, 2.0f);
        Sprite sprite3 = createTestSprite("Sprite3", 3.0f, 3.0f);

        testScene.addSprite(sprite1);
        testScene.addSprite(sprite2);
        testScene.addSprite(sprite3);

        List<Sprite> sprites = sceneManager.getAllSprites();

        assertEquals("Should return all three sprites", 3, sprites.size());
        assertTrue("Should contain sprite1", sprites.contains(sprite1));
        assertTrue("Should contain sprite2", sprites.contains(sprite2));
        assertTrue("Should contain sprite3", sprites.contains(sprite3));
    }

    /**
     * Test that getAllSprites() returns a copy, not the original list.
     * This ensures external modifications don't affect the scene.
     */
    @Test
    public void testGetAllSprites_ReturnsCopy() {
        Sprite sprite = createTestSprite("Sprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        List<Sprite> sprites1 = sceneManager.getAllSprites();
        List<Sprite> sprites2 = sceneManager.getAllSprites();

        assertNotSame("Should return different list instances", sprites1, sprites2);
        assertEquals("But should contain the same sprites", sprites1, sprites2);
    }

    /**
     * Test that getAllSprites() returns empty list when scene is null.
     */
    @Test
    public void testGetAllSprites_WithNullScene() {
        Context context = RuntimeEnvironment.getApplication();
        BaseSceneManager manager = new TestSceneManager(context, "NonExistentFile.json");
        manager.currentScene = null;

        List<Sprite> sprites = manager.getAllSprites();

        assertNotNull("Should return a list, not null", sprites);
        assertTrue("Should return empty list when scene is null", sprites.isEmpty());
    }

    // ==================== Aspect Ratio Calculation Tests ====================

    /**
     * Test that calculateAspectRatio() returns 1.0f for a square sprite.
     */
    @Test
    public void testCalculateAspectRatio_Square() {
        Sprite sprite = createTestSprite("Square", 5.0f, 5.0f);

        float aspectRatio = sceneManager.calculateAspectRatio(sprite);

        assertEquals("Aspect ratio of square should be 1.0", 1.0f, aspectRatio, 0.0001f);
    }

    /**
     * Test that calculateAspectRatio() returns correct ratio for rectangular sprite.
     */
    @Test
    public void testCalculateAspectRatio_Rectangle() {
        Sprite sprite = createTestSprite("Rectangle", 8.0f, 4.0f);

        float aspectRatio = sceneManager.calculateAspectRatio(sprite);

        assertEquals("Aspect ratio should be width/height = 8/4 = 2.0", 2.0f, aspectRatio, 0.0001f);
    }

    /**
     * Test that calculateAspectRatio() returns correct ratio for portrait sprite.
     */
    @Test
    public void testCalculateAspectRatio_PortraitSprite() {
        Sprite sprite = createTestSprite("Portrait", 4.0f, 8.0f);

        float aspectRatio = sceneManager.calculateAspectRatio(sprite);

        assertEquals("Aspect ratio should be width/height = 4/8 = 0.5", 0.5f, aspectRatio, 0.0001f);
    }

    /**
     * Test that calculateAspectRatio() returns 1.0f for null sprite.
     */
    @Test
    public void testCalculateAspectRatio_NullSprite() {
        float aspectRatio = sceneManager.calculateAspectRatio(null);

        assertEquals("Aspect ratio should be 1.0 for null sprite", 1.0f, aspectRatio, 0.0001f);
    }

    /**
     * Test that calculateAspectRatio() returns 1.0f when sprite has zero height.
     * This prevents division by zero.
     */
    @Test
    public void testCalculateAspectRatio_ZeroHeight() {
        Sprite sprite = createTestSpriteWithDimensions("ZeroHeight", 5.0f, 0.0f);

        float aspectRatio = sceneManager.calculateAspectRatio(sprite);

        assertEquals("Aspect ratio should be 1.0 when height is 0", 1.0f, aspectRatio, 0.0001f);
    }

    /**
     * Test that calculateAspectRatio() works with very small dimensions.
     */
    @Test
    public void testCalculateAspectRatio_SmallDimensions() {
        Sprite sprite = createTestSprite("Small", 0.001f, 0.002f);

        float aspectRatio = sceneManager.calculateAspectRatio(sprite);

        assertEquals("Should calculate correct aspect ratio for small dimensions", 0.5f, aspectRatio, 0.0001f);
    }

    /**
     * Test that calculateAspectRatio() works with large dimensions.
     */
    @Test
    public void testCalculateAspectRatio_LargeDimensions() {
        Sprite sprite = createTestSprite("Large", 1000.0f, 500.0f);

        float aspectRatio = sceneManager.calculateAspectRatio(sprite);

        assertEquals("Should calculate correct aspect ratio for large dimensions", 2.0f, aspectRatio, 0.0001f);
    }

    // ==================== State Change Tests ====================

    /**
     * Test that requestSpriteResort() sets the shouldResortSprites flag to true.
     */
    @Test
    public void testRequestSpriteResort_SetsFlagToTrue() {
        assertFalse("Initial flag should be false", sceneManager.shouldResortSprites);

        sceneManager.requestSpriteResort();

        assertTrue("Flag should be true after requesting resort", sceneManager.shouldResortSprites);
    }

    /**
     * Test that requestSpriteResort() can be called multiple times.
     */
    @Test
    public void testRequestSpriteResort_MultipleCalls() {
        sceneManager.requestSpriteResort();
        assertTrue("Flag should be true after first call", sceneManager.shouldResortSprites);

        sceneManager.requestSpriteResort();
        assertTrue("Flag should still be true after second call", sceneManager.shouldResortSprites);
    }

    /**
     * Test that updateScrollOffsetFromXFocus() correctly calculates scroll offset.
     */
    @Test
    public void testUpdateScrollOffsetFromXFocus_LeftFocus() {
        // xFocus = 0.0 (left) should give (0.5 - 0.0) * 5.0 = 2.5
        sceneManager.updateScrollOffsetFromXFocus(0.0f);

        float offset = sceneManager.scrollOffsetProcessor.updateAndGetCurrentOffset();
        assertEquals("Scroll offset at left focus should be 2.5", 2.5f, offset, 0.0001f);
    }

    /**
     * Test that updateScrollOffsetFromXFocus() at center gives zero offset.
     */
    @Test
    public void testUpdateScrollOffsetFromXFocus_CenterFocus() {
        // xFocus = 0.5 (center) should give (0.5 - 0.5) * 5.0 = 0.0
        sceneManager.updateScrollOffsetFromXFocus(0.5f);

        float offset = sceneManager.scrollOffsetProcessor.updateAndGetCurrentOffset();
        assertEquals("Scroll offset at center focus should be 0.0", 0.0f, offset, 0.0001f);
    }

    /**
     * Test that updateScrollOffsetFromXFocus() at right focus.
     */
    @Test
    public void testUpdateScrollOffsetFromXFocus_RightFocus() {
        // xFocus = 1.0 (right) should give (0.5 - 1.0) * 5.0 = -2.5
        sceneManager.updateScrollOffsetFromXFocus(1.0f);

        float offset = sceneManager.scrollOffsetProcessor.updateAndGetCurrentOffset();
        assertEquals("Scroll offset at right focus should be -2.5", -2.5f, offset, 0.0001f);
    }

    /**
     * Test that updateScrollOffsetFromXFocus() works with intermediate values.
     */
    @Test
    public void testUpdateScrollOffsetFromXFocus_QuarterFocus() {
        // xFocus = 0.25 should give (0.5 - 0.25) * 5.0 = 1.25
        sceneManager.updateScrollOffsetFromXFocus(0.25f);

        float offset = sceneManager.scrollOffsetProcessor.updateAndGetCurrentOffset();
        assertEquals("Scroll offset at quarter focus should be 1.25", 1.25f, offset, 0.0001f);
    }

    /**
     * Test that updateScrollOffsetFromXFocus() updates multiple times.
     */
    @Test
    public void testUpdateScrollOffsetFromXFocus_SequentialUpdates() {
        sceneManager.updateScrollOffsetFromXFocus(0.0f);
        float offset1 = sceneManager.scrollOffsetProcessor.updateAndGetCurrentOffset();
        assertEquals("First offset should be 2.5", 2.5f, offset1, 0.0001f);

        sceneManager.updateScrollOffsetFromXFocus(0.5f);
        float offset2 = sceneManager.scrollOffsetProcessor.updateAndGetCurrentOffset();
        assertEquals("Second offset should be 0.0", 0.0f, offset2, 0.0001f);

        sceneManager.updateScrollOffsetFromXFocus(1.0f);
        float offset3 = sceneManager.scrollOffsetProcessor.updateAndGetCurrentOffset();
        assertEquals("Third offset should be -2.5", -2.5f, offset3, 0.0001f);
    }

    // ==================== Sprite Selection Tests ====================

    /**
     * Test that selectSpriteByIndex() returns null when scene is null.
     */
    @Test
    public void testSelectSpriteByIndex_NullScene() {
        Context context = RuntimeEnvironment.getApplication();
        BaseSceneManager manager = new TestSceneManager(context, "NonExistentFile.json");
        manager.currentScene = null;

        Sprite selected = manager.selectSpriteByIndex(0);

        assertNull("Should return null when scene is null", selected);
    }

    /**
     * Test that selectSpriteByIndex() returns null for negative index.
     */
    @Test
    public void testSelectSpriteByIndex_NegativeIndex() {
        Sprite sprite = createTestSprite("Sprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        Sprite selected = sceneManager.selectSpriteByIndex(-1);

        assertNull("Should return null for negative index", selected);
    }

    /**
     * Test that selectSpriteByIndex() returns null for out-of-bounds index.
     */
    @Test
    public void testSelectSpriteByIndex_OutOfBounds() {
        Sprite sprite = createTestSprite("Sprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        Sprite selected = sceneManager.selectSpriteByIndex(5);

        assertNull("Should return null for out-of-bounds index", selected);
    }

    /**
     * Test that selectSpriteByIndex() returns the correct sprite at valid index.
     */
    @Test
    public void testSelectSpriteByIndex_ValidIndex() {
        Sprite sprite1 = createTestSprite("Sprite1", 1.0f, 1.0f);
        Sprite sprite2 = createTestSprite("Sprite2", 2.0f, 2.0f);
        Sprite sprite3 = createTestSprite("Sprite3", 3.0f, 3.0f);

        testScene.addSprite(sprite1);
        testScene.addSprite(sprite2);
        testScene.addSprite(sprite3);

        Sprite selected = sceneManager.selectSpriteByIndex(1);

        assertEquals("Should return the sprite at index 1", sprite2, selected);
    }

    /**
     * Test that selectSpriteByIndex() highlights the selected sprite.
     */
    @Test
    public void testSelectSpriteByIndex_HighlightsSelectedSprite() {
        Sprite sprite1 = createTestSprite("Sprite1", 1.0f, 1.0f);
        Sprite sprite2 = createTestSprite("Sprite2", 2.0f, 2.0f);

        testScene.addSprite(sprite1);
        testScene.addSprite(sprite2);

        sceneManager.selectSpriteByIndex(1);

        assertTrue("Selected sprite should be highlighted", sprite2.isShowEdgeHighlight());
        assertFalse("Non-selected sprite should not be highlighted", sprite1.isShowEdgeHighlight());
    }

    /**
     * Test that selectSpriteByIndex() deselects previous sprite.
     */
    @Test
    public void testSelectSpriteByIndex_DeselectsPreviousSprite() {
        Sprite sprite1 = createTestSprite("Sprite1", 1.0f, 1.0f);
        Sprite sprite2 = createTestSprite("Sprite2", 2.0f, 2.0f);

        testScene.addSprite(sprite1);
        testScene.addSprite(sprite2);

        sceneManager.selectSpriteByIndex(0);
        assertTrue("Sprite1 should be highlighted", sprite1.isShowEdgeHighlight());

        sceneManager.selectSpriteByIndex(1);
        assertFalse("Sprite1 should no longer be highlighted", sprite1.isShowEdgeHighlight());
        assertTrue("Sprite2 should be highlighted", sprite2.isShowEdgeHighlight());
    }

    /**
     * Test selectSpriteByIndex() at first valid index (0).
     */
    @Test
    public void testSelectSpriteByIndex_FirstIndex() {
        Sprite sprite = createTestSprite("Sprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        Sprite selected = sceneManager.selectSpriteByIndex(0);

        assertEquals("Should return first sprite", sprite, selected);
        assertEquals("Should set as selected sprite", sprite, sceneManager.getSelectedSprite());
    }

    /**
     * Test selectSpriteByIndex() at last valid index.
     */
    @Test
    public void testSelectSpriteByIndex_LastIndex() {
        Sprite sprite1 = createTestSprite("Sprite1", 1.0f, 1.0f);
        Sprite sprite2 = createTestSprite("Sprite2", 2.0f, 2.0f);
        Sprite sprite3 = createTestSprite("Sprite3", 3.0f, 3.0f);

        testScene.addSprite(sprite1);
        testScene.addSprite(sprite2);
        testScene.addSprite(sprite3);

        Sprite selected = sceneManager.selectSpriteByIndex(2);

        assertEquals("Should return last sprite", sprite3, selected);
        assertEquals("Should set as selected sprite", sprite3, sceneManager.getSelectedSprite());
    }

    // ==================== SetSelectedSprite Tests ====================

    /**
     * Test that setSelectedSprite() with null scene does nothing.
     */
    @Test
    public void testSetSelectedSprite_NullScene() {
        Context context = RuntimeEnvironment.getApplication();
        BaseSceneManager manager = new TestSceneManager(context, "NonExistentFile.json");
        manager.currentScene = null;
        Sprite sprite = createTestSprite("Sprite", 1.0f, 1.0f);

        manager.setSelectedSprite(sprite);

        assertNull("Selected sprite should remain null", manager.getSelectedSprite());
    }

    /**
     * Test that setSelectedSprite() highlights the selected sprite.
     */
    @Test
    public void testSetSelectedSprite_HighlightsSprite() {
        Sprite sprite = createTestSprite("Sprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        sceneManager.setSelectedSprite(sprite);

        assertTrue("Selected sprite should be highlighted", sprite.isShowEdgeHighlight());
    }

    /**
     * Test that setSelectedSprite() unhighlights other sprites.
     */
    @Test
    public void testSetSelectedSprite_UnhighlightsOthers() {
        Sprite sprite1 = createTestSprite("Sprite1", 1.0f, 1.0f);
        Sprite sprite2 = createTestSprite("Sprite2", 2.0f, 2.0f);

        testScene.addSprite(sprite1);
        testScene.addSprite(sprite2);

        sceneManager.setSelectedSprite(sprite1);
        sceneManager.setSelectedSprite(sprite2);

        assertFalse("Previous sprite should not be highlighted", sprite1.isShowEdgeHighlight());
        assertTrue("New sprite should be highlighted", sprite2.isShowEdgeHighlight());
    }

    /**
     * Test that setSelectedSprite(null) deselects all sprites.
     */
    @Test
    public void testSetSelectedSprite_NullDeselects() {
        Sprite sprite = createTestSprite("Sprite", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        sceneManager.setSelectedSprite(sprite);
        assertTrue("Sprite should be highlighted", sprite.isShowEdgeHighlight());

        sceneManager.setSelectedSprite(null);
        assertFalse("Sprite should be unhighlighted", sprite.isShowEdgeHighlight());
        assertNull("Selected sprite should be null", sceneManager.getSelectedSprite());
    }

    /**
     * Test that setSelectedSprite() stores the sprite name.
     */
    @Test
    public void testSetSelectedSprite_StoresSpriteNameWithSprite() {
        Sprite sprite = createTestSprite("TestName", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        sceneManager.setSelectedSprite(sprite);

        assertEquals("Should store the sprite name", "TestName", sceneManager.selectedSpriteName);
    }

    /**
     * Test that setSelectedSprite(null) clears the sprite name.
     */
    @Test
    public void testSetSelectedSprite_ClearsSpriteNameWithNull() {
        Sprite sprite = createTestSprite("TestName", 1.0f, 1.0f);
        testScene.addSprite(sprite);

        sceneManager.setSelectedSprite(sprite);
        assertEquals("Should have sprite name set", "TestName", sceneManager.selectedSpriteName);

        sceneManager.setSelectedSprite(null);
        assertNull("Should clear sprite name", sceneManager.selectedSpriteName);
    }

    // ==================== Helper Methods ====================

    /**
     * Create a test sprite with default parameters.
     */
    private Sprite createTestSprite(String name, float width, float height) {
        SpriteData data = new SpriteData();
        data.name = name;
        data.width = width;
        data.height = height;
        data.positionX = 0.0f;
        data.positionY = 0.0f;
        data.parallaxMultiplier = 1.0f;
        data.textureResourceId = android.R.drawable.ic_menu_camera;
        data.textureResource = "test_texture";
        return new Sprite(data);
    }

    /**
     * Create a test sprite with specific dimensions (used for testing edge cases).
     */
    private Sprite createTestSpriteWithDimensions(String name, float width, float height) {
        return createTestSprite(name, width, height);
    }
}



















