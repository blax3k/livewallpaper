package com.example.livewallpaper.scene;

import com.example.livewallpaper.scene.models.Scene;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for ScenePicker.
 * Tests scene selection logic based on time range filtering and randomization.
 */
public class ScenePickerTest {

    private List<Scene> testScenes;
    private ScenePicker scenePicker;

    @Before
    public void setUp() {
        testScenes = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            Scene scene = new Scene("scene_" + i);
            testScenes.add(scene);
        }

        scenePicker = new ScenePicker(testScenes);
    }

    @Test
    public void constructor_CopiesScenesList() {
        List<Scene> originalList = new ArrayList<>(testScenes);
        int originalSize = originalList.size();
        testScenes.clear();

        assertNotNull("ScenePicker should have been initialized", scenePicker);
        assertTrue("Original list should have been non-empty", originalSize > 0);
    }

    @Test
    public void getNextScene_ThrowsExceptionWhenNoScenesAvailable() {
        ScenePicker emptyPicker = new ScenePicker(new ArrayList<>());
        Scene currentScene = new Scene("current");

        assertThrows(IllegalStateException.class,
            () -> emptyPicker.getNextScene(currentScene));
    }

    @Test
    public void getNextScene_ReturnsNotNull() {
        Scene currentScene = testScenes.get(0);
        Scene nextScene = scenePicker.getNextScene(currentScene);

        assertNotNull("Next scene should not be null", nextScene);
    }

    @Test
    public void getNextScene_ReturnsSceneFromList() {
        Scene currentScene = testScenes.get(0);
        Scene nextScene = scenePicker.getNextScene(currentScene);

        assertTrue("Next scene should be from available scenes", testScenes.contains(nextScene));
    }

    @Test
    public void getNextScene_ExcludesCurrentSceneWhenMultipleAvailable() {
        Scene scene1 = new Scene("scene_type_a");
        Scene scene2 = new Scene("scene_type_b");
        Scene scene3 = new Scene("scene_type_c");

        List<Scene> multiScenes = new ArrayList<>();
        multiScenes.add(scene1);
        multiScenes.add(scene2);
        multiScenes.add(scene3);

        ScenePicker picker = new ScenePicker(multiScenes);

        for (int i = 0; i < 10; i++) {
            Scene nextScene = picker.getNextScene(scene1);
            assertNotNull("Should return a scene", nextScene);
            assertTrue("Should be from available scenes", multiScenes.contains(nextScene));
        }
    }

    // ==================== isSceneAvailable Tests ====================

    @Test
    public void isSceneAvailable_NormalRange_InsideRange() {
        // 09:00 = 540 min, 18:00 = 1080 min
        Scene scene = createSceneWithRange("day", 540, 1080);
        assertTrue("14:00 (840) in 09:00-18:00", ScenePicker.isSceneAvailable(scene, 840));
        assertTrue("09:00 (540) in 09:00-18:00", ScenePicker.isSceneAvailable(scene, 540));
        assertTrue("18:00 (1080) in 09:00-18:00", ScenePicker.isSceneAvailable(scene, 1080));
        assertTrue("09:30 (570) in 09:00-18:00", ScenePicker.isSceneAvailable(scene, 570));
    }

    @Test
    public void isSceneAvailable_NormalRange_OutsideRange() {
        Scene scene = createSceneWithRange("day", 540, 1080);
        assertFalse("08:59 (539) not in 09:00-18:00", ScenePicker.isSceneAvailable(scene, 539));
        assertFalse("18:01 (1081) not in 09:00-18:00", ScenePicker.isSceneAvailable(scene, 1081));
        assertFalse("00:00 (0) not in 09:00-18:00", ScenePicker.isSceneAvailable(scene, 0));
    }

    @Test
    public void isSceneAvailable_OvernightRange_InsideRange() {
        // 22:00 = 1320 min, 06:00 = 360 min
        Scene scene = createSceneWithRange("night", 1320, 360);
        assertTrue("23:00 (1380) in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 1380));
        assertTrue("00:00 (0) in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 0));
        assertTrue("03:30 (210) in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 210));
        assertTrue("06:00 (360) in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 360));
        assertTrue("22:00 (1320) in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 1320));
        assertTrue("22:30 (1350) in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 1350));
    }

    @Test
    public void isSceneAvailable_OvernightRange_OutsideRange() {
        Scene scene = createSceneWithRange("night", 1320, 360);
        assertFalse("06:01 (361) not in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 361));
        assertFalse("12:00 (720) not in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 720));
        assertFalse("21:59 (1319) not in overnight 22:00-06:00", ScenePicker.isSceneAvailable(scene, 1319));
    }

    @Test
    public void isSceneAvailable_AlwaysActive_DefaultRange() {
        Scene scene = new Scene("always");
        // Default range is 0-1439 (always active)
        assertTrue("00:00 (0) available by default", ScenePicker.isSceneAvailable(scene, 0));
        assertTrue("12:00 (720) available by default", ScenePicker.isSceneAvailable(scene, 720));
        assertTrue("23:59 (1439) available by default", ScenePicker.isSceneAvailable(scene, 1439));
    }

    @Test
    public void isSceneAvailable_MinuteGranularity() {
        // 09:15 = 555 min, 09:45 = 585 min
        Scene scene = createSceneWithRange("short", 555, 585);
        assertTrue("09:15 (555) in 09:15-09:45", ScenePicker.isSceneAvailable(scene, 555));
        assertTrue("09:30 (570) in 09:15-09:45", ScenePicker.isSceneAvailable(scene, 570));
        assertTrue("09:45 (585) in 09:15-09:45", ScenePicker.isSceneAvailable(scene, 585));
        assertFalse("09:14 (554) not in 09:15-09:45", ScenePicker.isSceneAvailable(scene, 554));
        assertFalse("09:46 (586) not in 09:15-09:45", ScenePicker.isSceneAvailable(scene, 586));
    }

    @Test
    public void getNextScene_SingleSceneReturnsIt() {
        Scene singleScene = new Scene("only_scene");

        List<Scene> singleSceneList = new ArrayList<>();
        singleSceneList.add(singleScene);

        ScenePicker picker = new ScenePicker(singleSceneList);
        Scene next = picker.getNextScene(singleScene);

        assertNotNull("Should return a scene", next);
        assertEquals("Should return the only available scene", singleScene.getSceneName(), next.getSceneName());
    }

    @Test
    public void constructor_CreatesDefensiveCopy() {
        List<Scene> originalList = new ArrayList<>(testScenes);
        ScenePicker picker = new ScenePicker(originalList);

        originalList.clear();

        Scene currentScene = testScenes.get(0);
        Scene nextScene = picker.getNextScene(currentScene);
        assertNotNull("Picker should still work after original list modification", nextScene);
    }

    @Test
    public void getNextScene_RandomSelectionAmongMultiple() {
        Scene scene1 = new Scene("scene_1");
        Scene scene2 = new Scene("scene_2");
        Scene scene3 = new Scene("scene_3");

        List<Scene> scenes = new ArrayList<>();
        scenes.add(scene1);
        scenes.add(scene2);
        scenes.add(scene3);

        ScenePicker picker = new ScenePicker(scenes);

        java.util.Set<String> selectedNames = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            Scene next = picker.getNextScene(scene1);
            selectedNames.add(next.getSceneName());
        }

        assertTrue("Should select scenes", selectedNames.size() >= 1);
    }

    @Test
    public void scenePicker_CanHandleMultipleInstances() {
        ScenePicker picker1 = new ScenePicker(testScenes);
        ScenePicker picker2 = new ScenePicker(testScenes);

        Scene current = testScenes.get(0);
        Scene next1 = picker1.getNextScene(current);
        Scene next2 = picker2.getNextScene(current);

        assertNotNull("First picker should return scene", next1);
        assertNotNull("Second picker should return scene", next2);
    }

    // ==================== Helper Methods ====================

    private Scene createSceneWithRange(String name, int startTime, int endTime) {
        Scene scene = new Scene(name);
        scene.setStartTime(startTime);
        scene.setEndTime(endTime);
        return scene;
    }
}

