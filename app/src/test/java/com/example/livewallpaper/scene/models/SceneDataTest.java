package com.example.livewallpaper.scene.models;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for SceneData model class.
 * Tests data structure initialization and default values.
 */
public class SceneDataTest {

    private SceneData sceneData;

    @Before
    public void setUp() {
        sceneData = new SceneData();
    }

    // ==================== Initialization Tests ====================

    @Test
    public void constructor_InitializesWithDefaultValues() {
        assertNull("sceneName should be null by default", sceneData.sceneName);
        assertNull("sprites should be null by default", sceneData.sprites);
        assertEquals("xFocus should default to 0.5", 0.5f, sceneData.xFocus, 0.001f);
        assertEquals("timeOfDay should default to DAY", SceneData.TimeOfDay.DAY, sceneData.timeOfDay);
    }

    // ==================== Field Assignment Tests ====================

    @Test
    public void sceneNameAssignment_WorksCorrectly() {
        sceneData.sceneName = "TestScene";
        assertEquals("TestScene", sceneData.sceneName);
    }

    @Test
    public void xFocusAssignment_WorksCorrectly() {
        sceneData.xFocus = 0.25f;
        assertEquals(0.25f, sceneData.xFocus, 0.001f);

        sceneData.xFocus = 0.75f;
        assertEquals(0.75f, sceneData.xFocus, 0.001f);

        sceneData.xFocus = 0.0f;
        assertEquals(0.0f, sceneData.xFocus, 0.001f);

        sceneData.xFocus = 1.0f;
        assertEquals(1.0f, sceneData.xFocus, 0.001f);
    }

    @Test
    public void timeOfDayAssignment_WorksCorrectly() {
        sceneData.timeOfDay = SceneData.TimeOfDay.DAWN;
        assertEquals(SceneData.TimeOfDay.DAWN, sceneData.timeOfDay);

        sceneData.timeOfDay = SceneData.TimeOfDay.SUNSET;
        assertEquals(SceneData.TimeOfDay.SUNSET, sceneData.timeOfDay);

        sceneData.timeOfDay = SceneData.TimeOfDay.NIGHT;
        assertEquals(SceneData.TimeOfDay.NIGHT, sceneData.timeOfDay);
    }

    @Test
    public void spritesAssignment_WorksCorrectly() {
        SpriteData[] sprites = new SpriteData[2];
        sprites[0] = new SpriteData();
        sprites[1] = new SpriteData();

        sceneData.sprites = sprites;
        assertNotNull("sprites should not be null after assignment", sceneData.sprites);
        assertEquals("sprites array length should be 2", 2, sceneData.sprites.length);
    }

    // ==================== TimeOfDay Enum Tests ====================

    @Test
    public void timeOfDayEnum_ContainsAllValues() {
        SceneData.TimeOfDay[] values = SceneData.TimeOfDay.values();
        assertEquals("TimeOfDay should have 4 values", 4, values.length);
    }

    @Test
    public void timeOfDayEnum_ContainsExpectedValues() {
        assertTrue("Should contain DAWN", contains(SceneData.TimeOfDay.values(), SceneData.TimeOfDay.DAWN));
        assertTrue("Should contain DAY", contains(SceneData.TimeOfDay.values(), SceneData.TimeOfDay.DAY));
        assertTrue("Should contain SUNSET", contains(SceneData.TimeOfDay.values(), SceneData.TimeOfDay.SUNSET));
        assertTrue("Should contain NIGHT", contains(SceneData.TimeOfDay.values(), SceneData.TimeOfDay.NIGHT));
    }

    @Test
    public void timeOfDayEnum_CanBeConvertedFromString() {
        SceneData.TimeOfDay dawn = SceneData.TimeOfDay.valueOf("DAWN");
        assertEquals(SceneData.TimeOfDay.DAWN, dawn);

        SceneData.TimeOfDay night = SceneData.TimeOfDay.valueOf("NIGHT");
        assertEquals(SceneData.TimeOfDay.NIGHT, night);
    }

    // ==================== Multiple Instances Tests ====================

    @Test
    public void multipleInstances_AreIndependent() {
        SceneData scene1 = new SceneData();
        SceneData scene2 = new SceneData();

        scene1.sceneName = "Scene1";
        scene1.xFocus = 0.3f;

        scene2.sceneName = "Scene2";
        scene2.xFocus = 0.7f;

        assertEquals("Scene1", scene1.sceneName);
        assertEquals("Scene2", scene2.sceneName);
        assertEquals(0.3f, scene1.xFocus, 0.001f);
        assertEquals(0.7f, scene2.xFocus, 0.001f);
    }

    // ==================== Helper Methods ====================

    private boolean contains(SceneData.TimeOfDay[] array, SceneData.TimeOfDay value) {
        for (SceneData.TimeOfDay item : array) {
            if (item == value) {
                return true;
            }
        }
        return false;
    }
}

