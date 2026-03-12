package com.example.livewallpaper.scene.models;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for Scene model class.
 * Tests scene creation, sprite management, property getters/setters, and state modifications.
 */
@RunWith(RobolectricTestRunner.class)
public class SceneTest {

    private Scene scene;
    private static final String TEST_SCENE_NAME = "TestScene";

    @Before
    public void setUp() {
        scene = new Scene(TEST_SCENE_NAME);
    }

    // ==================== Initialization Tests ====================

    @Test
    public void constructor_InitializesWithCorrectSceneName() {
        Scene testScene = new Scene("MyScene");
        assertEquals("MyScene", testScene.getSceneName());
    }

    @Test
    public void constructor_InitializesWithDefaultValues() {
        assertEquals(0.5f, scene.getXFocus(), 0.001f);
        assertEquals(SceneData.TimeOfDay.DAY, scene.getTimeOfDay());
        assertNotNull("sprites list should not be null", scene.getSprites());
        assertEquals("sprites list should be empty initially", 0, scene.getSprites().size());
    }

    @Test
    public void constructor_InitializesWithEmptyThreadSafeList() {
        Scene testScene = new Scene("ThreadSafeScene");
        // Test that we can add to the list from different contexts
        testScene.addSprite(createTestSprite("sprite1"));
        assertEquals(1, testScene.getSprites().size());
    }

    // ==================== Scene Name Tests ====================

    @Test
    public void getSceneName_ReturnsCorrectName() {
        Scene scene1 = new Scene("Scene1");
        assertEquals("Scene1", scene1.getSceneName());

        Scene scene2 = new Scene("DifferentScene");
        assertEquals("DifferentScene", scene2.getSceneName());
    }

    // ==================== XFocus Property Tests ====================

    @Test
    public void xFocusProperty_HasDefaultValue() {
        assertEquals(0.5f, scene.getXFocus(), 0.001f);
    }

    @Test
    public void setXFocus_UpdatesXFocus() {
        scene.setXFocus(0.25f);
        assertEquals(0.25f, scene.getXFocus(), 0.001f);

        scene.setXFocus(0.75f);
        assertEquals(0.75f, scene.getXFocus(), 0.001f);
    }

    @Test
    public void setXFocus_AcceptsEdgeCaseValues() {
        scene.setXFocus(0.0f);
        assertEquals(0.0f, scene.getXFocus(), 0.001f);

        scene.setXFocus(1.0f);
        assertEquals(1.0f, scene.getXFocus(), 0.001f);
    }

    // ==================== TimeOfDay Property Tests ====================

    @Test
    public void timeOfDayProperty_HasDefaultValue() {
        assertEquals(SceneData.TimeOfDay.DAY, scene.getTimeOfDay());
    }

    @Test
    public void setTimeOfDay_UpdatesTimeOfDay() {
        scene.setTimeOfDay(SceneData.TimeOfDay.DAWN);
        assertEquals(SceneData.TimeOfDay.DAWN, scene.getTimeOfDay());

        scene.setTimeOfDay(SceneData.TimeOfDay.SUNSET);
        assertEquals(SceneData.TimeOfDay.SUNSET, scene.getTimeOfDay());

        scene.setTimeOfDay(SceneData.TimeOfDay.NIGHT);
        assertEquals(SceneData.TimeOfDay.NIGHT, scene.getTimeOfDay());
    }

    @Test
    public void setTimeOfDay_AllEnumValues() {
        for (SceneData.TimeOfDay timeOfDay : SceneData.TimeOfDay.values()) {
            scene.setTimeOfDay(timeOfDay);
            assertEquals(timeOfDay, scene.getTimeOfDay());
        }
    }

    // ==================== Sprite Management Tests ====================

    @Test
    public void addSprite_AddsToEmptyScene() {
        Sprite sprite = createTestSprite("sprite1");
        scene.addSprite(sprite);

        assertEquals("Scene should contain 1 sprite", 1, scene.getSprites().size());
        assertEquals("sprite1", scene.getSprites().get(0).getName());
    }

    @Test
    public void addSprite_AddsMultipleSprites() {
        Sprite sprite1 = createTestSprite("sprite1");
        Sprite sprite2 = createTestSprite("sprite2");
        Sprite sprite3 = createTestSprite("sprite3");

        scene.addSprite(sprite1);
        scene.addSprite(sprite2);
        scene.addSprite(sprite3);

        assertEquals("Scene should contain 3 sprites", 3, scene.getSprites().size());
    }

    @Test
    public void getSprites_ReturnsThreadSafeList() {
        Sprite sprite1 = createTestSprite("sprite1");
        scene.addSprite(sprite1);

        // The list should be synchronized
        assertNotNull("sprites list should not be null", scene.getSprites());
        assertEquals(1, scene.getSprites().size());
    }

    @Test
    public void keepOnlySprite_RemovesOtherSprites() {
        scene.addSprite(createTestSprite("sprite1"));
        scene.addSprite(createTestSprite("sprite2"));
        scene.addSprite(createTestSprite("sprite3"));

        scene.keepOnlySprite("sprite2");

        assertEquals("Scene should contain 1 sprite", 1, scene.getSprites().size());
        assertEquals("sprite2", scene.getSprites().get(0).getName());
    }

    @Test
    public void keepOnlySprite_WithNonExistentSprite_RemovesAll() {
        scene.addSprite(createTestSprite("sprite1"));
        scene.addSprite(createTestSprite("sprite2"));

        scene.keepOnlySprite("nonexistent");

        assertEquals("Scene should be empty", 0, scene.getSprites().size());
    }

    @Test
    public void keepOnlySprite_WithSingleSprite_KeepsIt() {
        scene.addSprite(createTestSprite("sprite1"));

        scene.keepOnlySprite("sprite1");

        assertEquals("Scene should contain 1 sprite", 1, scene.getSprites().size());
        assertEquals("sprite1", scene.getSprites().get(0).getName());
    }

    @Test
    public void keepOnlySprite_WithEmptyScene_RemainsEmpty() {
        scene.keepOnlySprite("anysprite");

        assertEquals("Scene should remain empty", 0, scene.getSprites().size());
    }

    // ==================== Wipe Progress Tests ====================

    @Test
    public void updateWipeProgress_WithNoTransitioningSprites_DoesNotThrow() {
        scene.addSprite(createTestSprite("sprite1"));

        // Should not throw an exception
        scene.updateWipeProgress(0.5f);
    }

    @Test
    public void updateWipeProgress_PropagatesToTransitioningSprites() {
        Sprite sprite = createTestSprite("sprite1");
        scene.addSprite(sprite);

        // Set sprite to transitioning state
        sprite.setWipingIn(true);

        scene.updateWipeProgress(0.5f);
        assertEquals(0.5f, sprite.getWipeProgress(), 0.001f);
    }

    @Test
    public void updateWipeProgress_WithMultipleTransitioningSprites() {
        Sprite sprite1 = createTestSprite("sprite1");
        Sprite sprite2 = createTestSprite("sprite2");
        Sprite sprite3 = createTestSprite("sprite3");

        sprite1.setWipingIn(true);
        sprite2.setWipingOut(true);
        // sprite3 is not transitioning

        scene.addSprite(sprite1);
        scene.addSprite(sprite2);
        scene.addSprite(sprite3);

        scene.updateWipeProgress(0.75f);

        assertEquals(0.75f, sprite1.getWipeProgress(), 0.001f);
        assertEquals(0.75f, sprite2.getWipeProgress(), 0.001f);
    }

    @Test
    public void updateWipeProgress_WithProgressRange() {
        Sprite sprite = createTestSprite("sprite1");
        sprite.setWipingIn(true);
        scene.addSprite(sprite);

        scene.updateWipeProgress(0.0f);
        assertEquals(0.0f, sprite.getWipeProgress(), 0.001f);

        scene.updateWipeProgress(0.5f);
        assertEquals(0.5f, sprite.getWipeProgress(), 0.001f);

        scene.updateWipeProgress(1.0f);
        assertEquals(1.0f, sprite.getWipeProgress(), 0.001f);
    }

    // ==================== Multiple Scene Tests ====================

    @Test
    public void multipleScenes_AreIndependent() {
        Scene scene1 = new Scene("Scene1");
        Scene scene2 = new Scene("Scene2");

        scene1.addSprite(createTestSprite("sprite1"));
        scene2.addSprite(createTestSprite("sprite2"));
        scene2.addSprite(createTestSprite("sprite3"));

        assertEquals(1, scene1.getSprites().size());
        assertEquals(2, scene2.getSprites().size());
        assertEquals("Scene1", scene1.getSceneName());
        assertEquals("Scene2", scene2.getSceneName());
    }

    // ==================== Combined Property Tests ====================

    @Test
    public void sceneWithComplexConfiguration() {
        scene.setXFocus(0.3f);
        scene.setTimeOfDay(SceneData.TimeOfDay.SUNSET);

        Sprite sprite1 = createTestSprite("bg");
        Sprite sprite2 = createTestSprite("player");

        sprite1.setWipingIn(true);

        scene.addSprite(sprite1);
        scene.addSprite(sprite2);

        // Verify all properties
        assertEquals("TestScene", scene.getSceneName());
        assertEquals(0.3f, scene.getXFocus(), 0.001f);
        assertEquals(SceneData.TimeOfDay.SUNSET, scene.getTimeOfDay());
        assertEquals(2, scene.getSprites().size());

        // Update wipe progress
        scene.updateWipeProgress(0.6f);
        assertEquals(0.6f, sprite1.getWipeProgress(), 0.001f);
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test sprite with minimal configuration.
     */
    private Sprite createTestSprite(String name) {
        SpriteData data = new SpriteData();
        data.name = name;
        data.width = 1.0f;
        data.height = 1.0f;
        data.positionX = 0.0f;
        data.positionY = 0.0f;
        data.parallaxMultiplier = 1.0f;
        data.textureResourceId = 0;
        data.textureResource = "test";
        return new Sprite(data);
    }
}





