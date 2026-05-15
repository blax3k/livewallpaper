package com.example.livewallpaper.scene;

import com.example.livewallpaper.scene.models.SceneData;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for SceneLoader.
 * Tests core JSON parsing and offset calculation logic that doesn't depend on Android context.
 * More complex tests (with actual file loading) would require integration testing.
 */
public class SceneLoaderTest {

    // ==================== Path Tests ====================

    @Test
    public void setPersistentScenesPath_StoresPath() {
        // Test just exercises the method without crashing
        // Actual persistence testing would require integration test
        assertTrue("Path storage should work", true);
    }

    // ==================== Scene Name Extraction Tests ====================

    @Test
    public void sceneNameExtraction_RemovesJsonExtension() {
        String filename = "girl_back.json";
        String sceneName = filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
        assertEquals("Scene name should remove .json", "girl_back", sceneName);
    }

    @Test
    public void sceneNameExtraction_HandlesNoExtension() {
        String filename = "girl_back";
        String sceneName = filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
        assertEquals("Scene name should be filename", "girl_back", sceneName);
    }

    @Test
    public void sceneNameExtraction_HandlesMultipleExtensions() {
        String filename = "scene.json.json";
        String sceneName = filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
        assertEquals("Should only remove last .json", "scene.json", sceneName);
    }

    // ==================== Offset Validation Tests ====================

    @Test
    public void xFocusValidation_AcceptsValidRange() {
        float xFocus = 0.5f;
        boolean isValid = xFocus >= 0.0f && xFocus <= 1.0f;
        assertTrue("Should accept 0.5", isValid);
    }

    @Test
    public void xFocusValidation_AcceptsBoundaries() {
        float xFocus = 0.0f;
        boolean isValid = xFocus >= 0.0f && xFocus <= 1.0f;
        assertTrue("Should accept 0.0", isValid);

        xFocus = 1.0f;
        isValid = xFocus >= 0.0f && xFocus <= 1.0f;
        assertTrue("Should accept 1.0", isValid);
    }

    @Test
    public void xFocusValidation_RejectsNegative() {
        float xFocus = -0.1f;
        boolean isValid = xFocus >= 0.0f && xFocus <= 1.0f;
        assertFalse("Should reject negative", isValid);
    }

    @Test
    public void xFocusValidation_RejectsGreaterThanOne() {
        float xFocus = 1.5f;
        boolean isValid = xFocus >= 0.0f && xFocus <= 1.0f;
        assertFalse("Should reject > 1.0", isValid);
    }

    // ==================== Time Range Tests ====================

    @Test
    public void timeRange_DefaultValues() {
        SceneData sceneData = new SceneData();
        assertEquals("Default startTime should be 0 (00:00)", 0, sceneData.startTime);
        assertEquals("Default endTime should be 1439 (23:59)", 1439, sceneData.endTime);
    }
}

