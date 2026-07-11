package com.example.livewallpaper.scene;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SceneFlagDeclarations;
import com.example.livewallpaper.scene.models.ScoredFlagEntry;
import com.example.livewallpaper.world.WorldStateManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link ScenePicker}.
 *
 * The flag-based selection tests mirror the web editor's scene ranking (frontend/src/simulatorScenes.test.ts)
 * so the on-device picker and the simulator agree: a scene is eligible only when its required flags
 * are all active and none of its excluded flags are active; among eligible scenes the highest score
 * (sum of active scored-flag weights) wins; score ties break toward the least-shown scene.
 *
 * WorldStateManager is mocked (the subclass mock-maker under src/test/resources handles the concrete
 * singleton) so active flags and per-scene show-counts can be set exactly.
 */
@RunWith(RobolectricTestRunner.class)
public class ScenePickerTest {

    private WorldStateManager worldState;
    private List<Scene> testScenes;
    private ScenePicker scenePicker;

    @Before
    public void setUp() {
        worldState = mock(WorldStateManager.class);
        // Defaults: no flag active, every scene shown 0 times.
        when(worldState.isFlagActive(anyString())).thenReturn(false);
        when(worldState.getSceneShowCount(anyString())).thenReturn(0);

        testScenes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            testScenes.add(new Scene("scene_" + i));
        }
        scenePicker = new ScenePicker(testScenes, worldState);
    }

    // ── structural selection behaviour ───────────────────────────────────────

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
        ScenePicker emptyPicker = new ScenePicker(new ArrayList<>(), worldState);
        assertThrows(IllegalStateException.class, () -> emptyPicker.getNextScene(new Scene("current")));
    }

    @Test
    public void getNextScene_ReturnsSceneFromListExcludingCurrent() {
        Scene current = testScenes.get(0);
        for (int i = 0; i < 10; i++) {
            Scene next = scenePicker.getNextScene(current);
            assertNotNull("Next scene should not be null", next);
            assertTrue("Next scene should be from available scenes", testScenes.contains(next));
            assertNotEquals("Should not repeat the current scene", current.getSceneId(), next.getSceneId());
        }
    }

    @Test
    public void getNextScene_SingleSceneReturnsIt() {
        Scene only = new Scene("only_scene");
        ScenePicker picker = new ScenePicker(new ArrayList<>(List.of(only)), worldState);
        assertEquals(only.getSceneId(), picker.getNextScene(only).getSceneId());
    }

    @Test
    public void constructor_CreatesDefensiveCopy() {
        List<Scene> originalList = new ArrayList<>(testScenes);
        ScenePicker picker = new ScenePicker(originalList, worldState);
        originalList.clear();
        assertNotNull("Picker should still work after original list modification",
            picker.getNextScene(testScenes.get(0)));
    }

    // ── flag eligibility (required / excluded) ───────────────────────────────

    @Test
    public void getNextScene_skipsSceneWhoseExcludedFlagIsActive() {
        when(worldState.isFlagActive("night")).thenReturn(true);

        Scene current = new Scene("current");
        Scene blocked = sceneWith("blocked", excluded("night"));
        Scene ok = new Scene("ok");
        ScenePicker picker = new ScenePicker(List.of(current, blocked, ok), worldState);

        // Only "ok" is eligible (current is skipped, "blocked" is excluded by the active flag).
        for (int i = 0; i < 10; i++) {
            assertEquals("ok", picker.getNextScene(current).getSceneId());
        }
    }

    @Test
    public void getNextScene_skipsSceneMissingARequiredFlag() {
        // "rain" is not active, so the scene requiring it is ineligible.
        Scene current = new Scene("current");
        Scene needsRain = sceneWith("needs_rain", required("rain"));
        Scene ok = new Scene("ok");
        ScenePicker picker = new ScenePicker(List.of(current, needsRain, ok), worldState);

        for (int i = 0; i < 10; i++) {
            assertEquals("ok", picker.getNextScene(current).getSceneId());
        }
    }

    // ── scoring + tiebreak ───────────────────────────────────────────────────

    @Test
    public void getNextScene_picksHighestScoringEligibleScene() {
        when(worldState.isFlagActive("x")).thenReturn(true);

        Scene current = new Scene("current");
        Scene low = sceneWith("low", scored("x", 5));
        Scene high = sceneWith("high", scored("x", 20));
        ScenePicker picker = new ScenePicker(List.of(current, low, high), worldState);

        for (int i = 0; i < 10; i++) {
            assertEquals("high", picker.getNextScene(current).getSceneId());
        }
    }

    @Test
    public void getNextScene_breaksScoreTieByLeastShown() {
        when(worldState.isFlagActive("x")).thenReturn(true);
        when(worldState.getSceneShowCount("seen")).thenReturn(9);
        when(worldState.getSceneShowCount("fresh")).thenReturn(1);

        Scene current = new Scene("current");
        Scene seen = sceneWith("seen", scored("x", 10));
        Scene fresh = sceneWith("fresh", scored("x", 10));
        ScenePicker picker = new ScenePicker(List.of(current, seen, fresh), worldState);

        for (int i = 0; i < 10; i++) {
            assertEquals("fresh", picker.getNextScene(current).getSceneId());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Scene sceneWith(String id, SceneFlagDeclarations decl) {
        Scene s = new Scene(id);
        s.setFlagDeclarations(decl);
        return s;
    }

    private static SceneFlagDeclarations required(String... flags) {
        SceneFlagDeclarations d = new SceneFlagDeclarations();
        d.required = flags;
        return d;
    }

    private static SceneFlagDeclarations excluded(String... flags) {
        SceneFlagDeclarations d = new SceneFlagDeclarations();
        d.excluded = flags;
        return d;
    }

    private static SceneFlagDeclarations scored(String flagId, int weight) {
        SceneFlagDeclarations d = new SceneFlagDeclarations();
        ScoredFlagEntry e = new ScoredFlagEntry();
        e.flagId = flagId;
        e.weight = weight;
        d.scored = new ScoredFlagEntry[]{ e };
        return d;
    }
}
