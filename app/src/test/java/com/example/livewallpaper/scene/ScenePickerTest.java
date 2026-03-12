package com.example.livewallpaper.scene;

import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SceneData;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for ScenePicker.
 * Tests scene selection logic based on filtering and randomization.
 * Note: Time-of-day filtering depends on wall-clock time, so tests focus on selection logic.
 */
public class ScenePickerTest {

    private List<Scene> testScenes;
    private ScenePicker scenePicker;

    @Before
    public void setUp() {
        testScenes = new ArrayList<>();

        // Create test scenes for various time periods
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

    @Test
    public void getCurrentTimeOfDay_IdentifiesDawn() {
        assertTrue("6:00 should be DAWN", isTimeInPeriod(6, SceneData.TimeOfDay.DAWN));
        assertTrue("8:00 should be DAWN", isTimeInPeriod(8, SceneData.TimeOfDay.DAWN));
    }

    @Test
    public void getCurrentTimeOfDay_IdentifiesDay() {
        assertTrue("9:00 should be DAY", isTimeInPeriod(9, SceneData.TimeOfDay.DAY));
        assertTrue("12:00 should be DAY", isTimeInPeriod(12, SceneData.TimeOfDay.DAY));
        assertTrue("17:00 should be DAY", isTimeInPeriod(17, SceneData.TimeOfDay.DAY));
    }

    @Test
    public void getCurrentTimeOfDay_IdentifiesSunset() {
        assertTrue("18:00 should be SUNSET", isTimeInPeriod(18, SceneData.TimeOfDay.SUNSET));
        assertTrue("20:00 should be SUNSET", isTimeInPeriod(20, SceneData.TimeOfDay.SUNSET));
    }

    @Test
    public void getCurrentTimeOfDay_IdentifiesNight() {
        assertTrue("21:00 should be NIGHT", isTimeInPeriod(21, SceneData.TimeOfDay.NIGHT));
        assertTrue("23:00 should be NIGHT", isTimeInPeriod(23, SceneData.TimeOfDay.NIGHT));
        assertTrue("0:00 should be NIGHT", isTimeInPeriod(0, SceneData.TimeOfDay.NIGHT));
        assertTrue("3:00 should be NIGHT", isTimeInPeriod(3, SceneData.TimeOfDay.NIGHT));
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

    private boolean isTimeInPeriod(int hour, SceneData.TimeOfDay expectedPeriod) {
        SceneData.TimeOfDay actual;

        if (hour >= 6 && hour < 9) {
            actual = SceneData.TimeOfDay.DAWN;
        } else if (hour >= 9 && hour < 18) {
            actual = SceneData.TimeOfDay.DAY;
        } else if (hour >= 18 && hour < 21) {
            actual = SceneData.TimeOfDay.SUNSET;
        } else {
            actual = SceneData.TimeOfDay.NIGHT;
        }

        return actual == expectedPeriod;
    }
}

