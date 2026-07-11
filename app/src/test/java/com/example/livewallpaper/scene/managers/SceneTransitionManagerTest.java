package com.example.livewallpaper.scene.managers;
import android.content.Context;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.Sprite;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
/**
 * Unit tests for SceneTransitionManager.
 * Tests transition lifecycle, sprite wipe effects, and texture cleanup.
 */
@RunWith(RobolectricTestRunner.class)
public class SceneTransitionManagerTest {
    private SceneTransitionManager transitionManager;
    @Mock
    private Context mockContext;
    @Mock
    private TextureManager mockTextureManager;
    private Scene oldScene;
    private Scene newScene;
    // Controllable clock backing the transition's fade timing, so completion is deterministic
    // instead of depending on wall-clock elapsed time.
    private long nowMs = 1_000L;
    // Advance well past the (private) 800ms fade duration to force completion on the next update.
    private static final long PAST_FADE_MS = 5_000L;

    @Before
    public void setUp() {
        // NOTE: openMocks returns an AutoCloseable — do NOT close it here (try-with-resources would
        // immediately tear the mocks down, leaving @Mock fields unusable during the tests).
        MockitoAnnotations.openMocks(this);
        // Inject the controllable clock and a synchronous executor so texture cleanup runs inline.
        transitionManager = new SceneTransitionManager(() -> nowMs, Runnable::run);
        // Create test scenes
        oldScene = new Scene("old_scene");
        newScene = new Scene("new_scene");
        // Add test sprites using proper Sprite constructor
        for (int i = 0; i < 3; i++) {
            Sprite oldSprite = new Sprite(1000 + i, "old_sprite_" + i, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, "texture_old_" + i, null);
            oldSprite.setTextureId(1000 + i);
            oldScene.addSprite(oldSprite);
            Sprite newSprite = new Sprite(2000 + i, "new_sprite_" + i, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, "texture_new_" + i, null);
            newSprite.setTextureId(2000 + i);
            newScene.addSprite(newSprite);
        }
    }
    @Test
    public void isTransitioning_ReturnsFalseInitially() {
        assertFalse("Should not be transitioning initially", transitionManager.isTransitioning());
    }
    @Test
    public void getNewScene_ReturnsNullInitially() {
        assertNull("Should return null when not transitioning", transitionManager.getNewScene());
    }
    @Test
    public void startTransition_InitiatesTransition() {
        transitionManager.startTransition(oldScene, newScene, mockContext);
        assertTrue("Should be transitioning after start", transitionManager.isTransitioning());
    }
    @Test
    public void startTransition_StoresOldAndNewScene() {
        transitionManager.startTransition(oldScene, newScene, mockContext);
        Scene newSceneRef = transitionManager.getNewScene();
        assertNotNull("New scene should be stored", newSceneRef);
        assertEquals("Should store the new scene", newScene.getSceneId(), newSceneRef.getSceneId());
    }
    @Test
    public void updateTransition_ReturnsNullWhenNotTransitioning() {
        Scene result = transitionManager.updateTransition(mockTextureManager);
        assertNull("Should return null when not transitioning", result);
    }
    @Test
    public void updateTransition_FinishesTransitionAfterDuration() {
        transitionManager.startTransition(oldScene, newScene, mockContext);
        transitionManager.updateTransition(mockTextureManager); // begins the fade at nowMs
        nowMs += PAST_FADE_MS;                                   // fade duration elapses
        transitionManager.updateTransition(mockTextureManager); // completes on this frame
        assertFalse("Transition should complete after the fade duration elapses", transitionManager.isTransitioning());
    }
    @Test
    public void startTransition_MarksOldSpritesForWipeOut() {
        // Capture the original old sprites first: beginFade also adds the new scene's wiping-IN
        // sprites into oldScene, so asserting over the combined list would wrongly include those.
        List<Sprite> originalOldSprites = new ArrayList<>(oldScene.getSprites());
        transitionManager.startTransition(oldScene, newScene, mockContext);
        transitionManager.updateTransition(mockTextureManager);
        for (Sprite sprite : originalOldSprites) {
            assertTrue("Old sprite should be marked for wipeout", sprite.isWipingOut());
        }
    }
    @Test
    public void transitionCompletion_RemovesAddedSpritesFromOldScene() {
        int oldSpriteCountBefore = oldScene.getSprites().size();
        transitionManager.startTransition(oldScene, newScene, mockContext);
        transitionManager.updateTransition(mockTextureManager);
        int newSpriteCountDuring = oldScene.getSprites().size();
        assertTrue("Old scene should have new sprites during transition",
            newSpriteCountDuring > oldSpriteCountBefore);
        nowMs += PAST_FADE_MS;
        transitionManager.updateTransition(mockTextureManager); // completes and removes added sprites
        int spriteCountAfter = oldScene.getSprites().size();
        assertEquals("Added sprites should be removed after transition",
            oldSpriteCountBefore, spriteCountAfter);
    }
    @Test
    public void transitionCompletion_CleansUpUnusedTextures() {
        transitionManager.startTransition(oldScene, newScene, mockContext);
        transitionManager.updateTransition(mockTextureManager); // begins the fade
        nowMs += PAST_FADE_MS;
        transitionManager.updateTransition(mockTextureManager); // completes; cleanup runs on the (synchronous) executor
        verify(mockTextureManager, atLeastOnce()).unloadUnusedTextures(any(), any());
    }
    @Test
    public void startTransition_WithNullContext() {
        transitionManager.startTransition(oldScene, newScene, null);
        assertTrue("Should start transition even with null context", transitionManager.isTransitioning());
    }
    @Test
    public void multipleTransitions_Sequential() {
        transitionManager.startTransition(oldScene, newScene, mockContext);
        transitionManager.updateTransition(mockTextureManager);
        nowMs += PAST_FADE_MS;
        transitionManager.updateTransition(mockTextureManager);
        assertFalse("First transition should complete", transitionManager.isTransitioning());
        Scene oldScene2 = newScene;
        Scene newScene2 = new Scene("new_scene_2");
        transitionManager.startTransition(oldScene2, newScene2, mockContext);
        transitionManager.updateTransition(mockTextureManager);
        nowMs += PAST_FADE_MS;
        transitionManager.updateTransition(mockTextureManager);
        assertFalse("Second transition should complete", transitionManager.isTransitioning());
    }
}
