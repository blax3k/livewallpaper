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
        assertEquals("startTime should default to 0", 0, sceneData.startTime);
        assertEquals("endTime should default to 1439", 1439, sceneData.endTime);
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
    public void startTimeAssignment_WorksCorrectly() {
        sceneData.startTime = 540;   // 09:00
        assertEquals(540, sceneData.startTime);

        sceneData.startTime = 0;
        assertEquals(0, sceneData.startTime);

        sceneData.startTime = 1439;
        assertEquals(1439, sceneData.startTime);
    }

    @Test
    public void endTimeAssignment_WorksCorrectly() {
        sceneData.endTime = 1080;  // 18:00
        assertEquals(1080, sceneData.endTime);

        sceneData.endTime = 0;
        assertEquals(0, sceneData.endTime);

        sceneData.endTime = 1439;
        assertEquals(1439, sceneData.endTime);
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

    // ==================== Multiple Instances Tests ====================

    @Test
    public void multipleInstances_AreIndependent() {
        SceneData scene1 = new SceneData();
        SceneData scene2 = new SceneData();

        scene1.sceneName = "Scene1";
        scene1.xFocus = 0.3f;
        scene1.startTime = 6;
        scene1.endTime = 12;
        scene1.startTime = 360;   // 06:00
        scene1.endTime = 720;     // 12:00

        scene2.startTime = 1080;  // 18:00
        scene2.endTime = 1439;    // 23:59

        assertEquals("Scene1", scene1.sceneName);
        assertEquals("Scene2", scene2.sceneName);
        assertEquals(0.3f, scene1.xFocus, 0.001f);
        assertEquals(0.7f, scene2.xFocus, 0.001f);
        assertEquals(360, scene1.startTime);
        assertEquals(1080, scene2.startTime);
    }
}

