package com.example.livewallpaper.scene.models;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/**
 * Unit tests for Sprite model class.
 * Tests sprite creation, property management, positioning, scaling, texture coordinates, and wipe state.
 */
@RunWith(RobolectricTestRunner.class)
public class SpriteTest {

    private Sprite sprite;

    @Before
    public void setUp() {
        SpriteData spriteData = new SpriteData();
        spriteData.name = "TestSprite";
        spriteData.width = 2.0f;
        spriteData.height = 3.0f;
        spriteData.parallaxMultiplier = 1.0f;
        spriteData.positionX = 1.0f;
        spriteData.positionY = 2.0f;
        spriteData.textureResourceId = 123;
        spriteData.textureResource = "background";
        spriteData.texCoordinates = new float[]{0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        sprite = new Sprite(spriteData);
    }

    // ==================== Construction Tests ====================

    @Test
    public void constructor_InitializesWithSpriteData() {
        assertEquals("TestSprite", sprite.getName());
        assertEquals(2.0f, sprite.getWidth(), 0.001f);
        assertEquals(3.0f, sprite.getHeight(), 0.001f);
        assertEquals(1.0f, sprite.getParallaxMultiplier(), 0.001f);
        assertEquals(1.0f, sprite.getPositionX(), 0.001f);
        assertEquals(2.0f, sprite.getPositionY(), 0.001f);
        assertEquals(123, sprite.getTextureResourceId());
        assertEquals("background", sprite.getTextureResource());
    }

    @Test
    public void constructor_InitializesBuffers() {
        assertNotNull("vertexBuffer should be initialized", sprite.getVertexBuffer());
        assertNotNull("texCoordBuffer should be initialized", sprite.getTexCoordBuffer());
        assertNotNull("parallaxMultiplierBuffer should be initialized", sprite.getParallaxMultiplierBuffer());
    }

    @Test
    public void constructor_WithNullTexCoordinates_UsesDefaults() {
        SpriteData data = new SpriteData();
        data.name = "DefaultCoords";
        data.width = 1.0f;
        data.height = 1.0f;
        data.texCoordinates = null;
        data.textureResourceId = 0;
        data.textureResource = "test";

        Sprite testSprite = new Sprite(data);
        float[] texCoords = testSprite.getTextureCoordinates();

        assertNotNull("texCoordinates should not be null", texCoords);
        assertEquals("Should have 8 coordinates", 8, texCoords.length);
    }

    // ==================== Position Tests ====================

    @Test
    public void setPosition_UpdatesPositionXAndY() {
        sprite.setPosition(5.0f, -2.0f);

        assertEquals(5.0f, sprite.getPositionX(), 0.001f);
        assertEquals(-2.0f, sprite.getPositionY(), 0.001f);
    }

    @Test
    public void setPositionX_UpdatesOnlyX() {
        sprite.setPositionX(5.0f);

        assertEquals(5.0f, sprite.getPositionX(), 0.001f);
        assertEquals(2.0f, sprite.getPositionY(), 0.001f);
    }

    @Test
    public void setPositionY_UpdatesOnlyY() {
        sprite.setPositionY(7.0f);

        assertEquals(1.0f, sprite.getPositionX(), 0.001f);
        assertEquals(7.0f, sprite.getPositionY(), 0.001f);
    }

    @Test
    public void setPositionAndUpdateOriginal_UpdatesBothCurrentAndOriginal() {
        sprite.setPositionAndUpdateOriginal(3.0f, 4.0f);

        assertEquals(3.0f, sprite.getPositionX(), 0.001f);
        assertEquals(4.0f, sprite.getPositionY(), 0.001f);
        assertEquals(3.0f, sprite.getOriginalPositionX(), 0.001f);
        assertEquals(4.0f, sprite.getOriginalPositionY(), 0.001f);
    }

    @Test
    public void setPositionXAndUpdateOriginal_UpdatesBothCurrentAndOriginalX() {
        sprite.setPositionXAndUpdateOriginal(6.0f);

        assertEquals(6.0f, sprite.getPositionX(), 0.001f);
        assertEquals(6.0f, sprite.getOriginalPositionX(), 0.001f);
    }

    @Test
    public void setPositionYAndUpdateOriginal_UpdatesBothCurrentAndOriginalY() {
        sprite.setPositionYAndUpdateOriginal(8.0f);

        assertEquals(8.0f, sprite.getPositionY(), 0.001f);
        assertEquals(8.0f, sprite.getOriginalPositionY(), 0.001f);
    }

    @Test
    public void resetPosition_RestoresOriginalPosition() {
        sprite.setPositionAndUpdateOriginal(10.0f, 11.0f);
        sprite.setPosition(20.0f, 21.0f);

        sprite.resetPosition();

        assertEquals(10.0f, sprite.getPositionX(), 0.001f);
        assertEquals(11.0f, sprite.getPositionY(), 0.001f);
    }

    // ==================== Dimension Tests ====================

    @Test
    public void setWidth_UpdatesWidth() {
        sprite.setWidth(5.0f);
        assertEquals(5.0f, sprite.getWidth(), 0.001f);
        assertEquals(3.0f, sprite.getHeight(), 0.001f);
    }

    @Test
    public void setHeight_UpdatesHeight() {
        sprite.setHeight(6.0f);
        assertEquals(2.0f, sprite.getWidth(), 0.001f);
        assertEquals(6.0f, sprite.getHeight(), 0.001f);
    }

    @Test
    public void setWidthAndUpdateOriginal_UpdatesBothCurrentAndOriginal() {
        sprite.setWidthAndUpdateOriginal(4.0f);

        assertEquals(4.0f, sprite.getWidth(), 0.001f);
        assertEquals(4.0f, sprite.getOriginalWidth(), 0.001f);
    }

    @Test
    public void setHeightAndUpdateOriginal_UpdatesBothCurrentAndOriginal() {
        sprite.setHeightAndUpdateOriginal(7.0f);

        assertEquals(7.0f, sprite.getHeight(), 0.001f);
        assertEquals(7.0f, sprite.getOriginalHeight(), 0.001f);
    }

    // ==================== Scaling Tests ====================

    @Test
    public void scaleFromOriginal_ScalesBasedOnFactor() {
        sprite.setWidthAndUpdateOriginal(10.0f);
        sprite.setHeightAndUpdateOriginal(20.0f);

        sprite.scaleFromOriginal(1.5f);

        assertEquals(15.0f, sprite.getWidth(), 0.001f);
        assertEquals(30.0f, sprite.getHeight(), 0.001f);
    }

    @Test
    public void scaleFromOriginal_WithFactorLessThanOne() {
        sprite.setWidthAndUpdateOriginal(10.0f);
        sprite.setHeightAndUpdateOriginal(20.0f);

        sprite.scaleFromOriginal(0.5f);

        assertEquals(5.0f, sprite.getWidth(), 0.001f);
        assertEquals(10.0f, sprite.getHeight(), 0.001f);
    }

    @Test
    public void resetScale_RestoresOriginalDimensions() {
        sprite.setWidthAndUpdateOriginal(10.0f);
        sprite.setHeightAndUpdateOriginal(20.0f);
        sprite.scaleFromOriginal(2.0f);

        sprite.resetScale();

        assertEquals(10.0f, sprite.getWidth(), 0.001f);
        assertEquals(20.0f, sprite.getHeight(), 0.001f);
    }

    // ==================== Parallax Multiplier Tests ====================

    @Test
    public void setParallaxMultiplier_UpdatesMultiplier() {
        sprite.setParallaxMultiplier(0.75f);
        assertEquals(0.75f, sprite.getParallaxMultiplier(), 0.001f);
    }

    @Test
    public void setParallaxMultiplier_WithVariousValues() {
        sprite.setParallaxMultiplier(0.0f);
        assertEquals(0.0f, sprite.getParallaxMultiplier(), 0.001f);

        sprite.setParallaxMultiplier(1.0f);
        assertEquals(1.0f, sprite.getParallaxMultiplier(), 0.001f);

        sprite.setParallaxMultiplier(2.5f);
        assertEquals(2.5f, sprite.getParallaxMultiplier(), 0.001f);
    }

    // ==================== Name and Texture Resource Tests ====================

    @Test
    public void setName_UpdatesName() {
        sprite.setName("NewName");
        assertEquals("NewName", sprite.getName());
    }

    @Test
    public void setTextureResource_UpdatesResource() {
        sprite.setTextureResource("newResource");
        assertEquals("newResource", sprite.getTextureResource());
    }

    @Test
    public void setTextureResourceId_UpdatesResourceId() {
        sprite.setTextureResourceId(999);
        assertEquals(999, sprite.getTextureResourceId());
    }

    @Test
    public void setTextureId_UpdatesGLTextureId() {
        sprite.setTextureId(42);
        assertEquals(42, sprite.getTextureId());
    }

    // ==================== Wipe Progress Tests ====================

    @Test
    public void setWipeProgress_UpdatesProgress() {
        sprite.setWipeProgress(0.5f);
        assertEquals(0.5f, sprite.getWipeProgress(), 0.001f);
    }

    @Test
    public void setWipeProgress_WithVariousValues() {
        sprite.setWipeProgress(0.0f);
        assertEquals(0.0f, sprite.getWipeProgress(), 0.001f);

        sprite.setWipeProgress(1.0f);
        assertEquals(1.0f, sprite.getWipeProgress(), 0.001f);

        sprite.setWipeProgress(0.75f);
        assertEquals(0.75f, sprite.getWipeProgress(), 0.001f);
    }

    // ==================== Wipe State Tests ====================

    @Test
    public void setWipingOut_UpdatesWipeOutState() {
        sprite.setWipingOut(true);
        assertTrue("Sprite should be wiping out", sprite.isWipingOut());

        sprite.setWipingOut(false);
        assertFalse("Sprite should not be wiping out", sprite.isWipingOut());
    }

    @Test
    public void setWipingIn_UpdatesWipeInState() {
        sprite.setWipingIn(true);
        assertTrue("Sprite should be wiping in", sprite.isWipingIn());

        sprite.setWipingIn(false);
        assertFalse("Sprite should not be wiping in", sprite.isWipingIn());
    }

    @Test
    public void isTransitioning_ReturnsTrueWhenWipingIn() {
        sprite.setWipingIn(true);
        assertTrue("Sprite should be transitioning", sprite.isTransitioning());
    }

    @Test
    public void isTransitioning_ReturnsTrueWhenWipingOut() {
        sprite.setWipingOut(true);
        assertTrue("Sprite should be transitioning", sprite.isTransitioning());
    }

    @Test
    public void isTransitioning_ReturnsFalseWhenNotWiping() {
        sprite.setWipingIn(false);
        sprite.setWipingOut(false);
        assertFalse("Sprite should not be transitioning", sprite.isTransitioning());
    }

    @Test
    public void resetWipe_ClearsWipeState() {
        sprite.setWipingIn(true);
        sprite.setWipeProgress(0.75f);

        sprite.resetWipe();

        assertFalse("Sprite should not be wiping in", sprite.isWipingIn());
        assertFalse("Sprite should not be transitioning", sprite.isTransitioning());
    }

    // ==================== Gyro Scaling Tests ====================

    @Test
    public void setGyroScaled_UpdatesGyroScaleState() {
        sprite.setGyroScaled(true);
        // Note: There's no getter for isGyroScaled, but setting should not throw

        sprite.setGyroScaled(false);
        // Setting to false should also work without exception
    }

    // ==================== Edge Highlight Tests ====================

    @Test
    public void setShowEdgeHighlight_UpdatesHighlightState() {
        sprite.setShowEdgeHighlight(true);
        assertTrue("Edge highlight should be enabled", sprite.isShowEdgeHighlight());

        sprite.setShowEdgeHighlight(false);
        assertFalse("Edge highlight should be disabled", sprite.isShowEdgeHighlight());
    }

    // ==================== Position at Zero Tests ====================

    @Test
    public void setPositionAtZero_UpdatesRenderingPosition() {
        sprite.setPositionAtZero(true);
        // This should affect vertex buffer but no direct getter exists

        sprite.setPositionAtZero(false);
        // Position should be restored to actual position
    }

    // ==================== Texture Coordinates Tests ====================

    @Test
    public void getTextureCoordinates_ReturnsCurrentCoordinates() {
        float[] texCoords = sprite.getTextureCoordinates();

        assertNotNull("texCoordinates should not be null", texCoords);
        assertEquals("Should have 8 coordinates", 8, texCoords.length);
    }

    @Test
    public void getOriginalTextureCoordinates_ReturnsCopy() {
        float[] original1 = sprite.getOriginalTextureCoordinates();
        float[] original2 = sprite.getOriginalTextureCoordinates();

        assertNotNull("original coordinates should not be null", original1);
        assertNotNull("second copy should not be null", original2);

        // They should be equal but not the same object
        assertArrayEquals("Coordinates should have same values", original1, original2, 0.001f);
        assertNotSame("Should be different object (copy)", original1, original2);
    }

    @Test
    public void setTextureCoordinates_UpdatesCoordinates() {
        float[] newCoords = {0.1f, 0.9f, 0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.1f};
        sprite.setTextureCoordinates(newCoords);

        float[] retrieved = sprite.getTextureCoordinates();
        assertArrayEquals("Coordinates should be updated", newCoords, retrieved, 0.001f);
    }

    @Test
    public void setTextureCoordinates_WithInvalidInput_DoesNotThrow() {
        sprite.setTextureCoordinates(null);
        sprite.setTextureCoordinates(new float[4]); // Wrong length
    }

    // ==================== Texture Editing Baseline Tests ====================

    @Test
    public void setTextureEditingBaseline_UpdatesBaseline() {
        sprite.setTextureEditingBaseline(5.0f, 7.0f);

        assertEquals(5.0f, sprite.getTextureEditingBaselineWidth(), 0.001f);
        assertEquals(7.0f, sprite.getTextureEditingBaselineHeight(), 0.001f);
    }

    @Test
    public void textureEditingBaseline_DefaultsToOriginalDimensions() {
        SpriteData data = new SpriteData();
        data.name = "TestSprite";
        data.width = 10.0f;
        data.height = 15.0f;
        data.textureResourceId = 0;
        data.textureResource = "test";

        Sprite newSprite = new Sprite(data);

        assertEquals(10.0f, newSprite.getTextureEditingBaselineWidth(), 0.001f);
        assertEquals(15.0f, newSprite.getTextureEditingBaselineHeight(), 0.001f);
    }

    // ==================== Buffer Access Tests ====================

    @Test
    public void getVertexCount_ReturnsCorrectValue() {
        assertEquals(4, sprite.getVertexCount());
    }

    @Test
    public void getEdgeLineVertexCount_ReturnsCorrectValue() {
        assertEquals(5, sprite.getEdgeLineVertexCount());
    }

    @Test
    public void getBuffers_AreNotNull() {
        assertNotNull("vertexBuffer should not be null", sprite.getVertexBuffer());
        assertNotNull("texCoordBuffer should not be null", sprite.getTexCoordBuffer());
        assertNotNull("normalizedPositionBuffer should not be null", sprite.getNormalizedPositionBuffer());
        assertNotNull("parallaxMultiplierBuffer should not be null", sprite.getParallaxMultiplierBuffer());
        assertNotNull("edgeLineBuffer should not be null", sprite.getEdgeLineBuffer());
        assertNotNull("edgeLineParallaxMultiplierBuffer should not be null", sprite.getEdgeLineParallaxMultiplierBuffer());
    }

    // ==================== Multiple Sprites Tests ====================

    @Test
    public void multipleSprites_AreIndependent() {
        SpriteData data1 = new SpriteData();
        data1.name = "sprite1";
        data1.width = 1.0f;
        data1.height = 1.0f;
        data1.textureResourceId = 1;
        data1.textureResource = "tex1";

        SpriteData data2 = new SpriteData();
        data2.name = "sprite2";
        data2.width = 2.0f;
        data2.height = 2.0f;
        data2.textureResourceId = 2;
        data2.textureResource = "tex2";

        Sprite sprite1 = new Sprite(data1);
        Sprite sprite2 = new Sprite(data2);

        sprite1.setPosition(1.0f, 1.0f);
        sprite2.setPosition(5.0f, 5.0f);

        assertEquals("sprite1", sprite1.getName());
        assertEquals("sprite2", sprite2.getName());
        assertEquals(1.0f, sprite1.getPositionX(), 0.001f);
        assertEquals(5.0f, sprite2.getPositionX(), 0.001f);
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void destroy_DoesNotThrow() {
        sprite.destroy();
        // Should not throw any exceptions
    }

    @Test
    public void setPosition_WithZeroValues() {
        sprite.setPosition(0.0f, 0.0f);

        assertEquals(0.0f, sprite.getPositionX(), 0.001f);
        assertEquals(0.0f, sprite.getPositionY(), 0.001f);
    }

    @Test
    public void setDimensions_WithZeroValues() {
        sprite.setWidth(0.0f);
        sprite.setHeight(0.0f);

        assertEquals(0.0f, sprite.getWidth(), 0.001f);
        assertEquals(0.0f, sprite.getHeight(), 0.001f);
    }

    @Test
    public void setDimensions_WithNegativeValues() {
        sprite.setWidth(-5.0f);
        sprite.setHeight(-10.0f);

        assertEquals(-5.0f, sprite.getWidth(), 0.001f);
        assertEquals(-10.0f, sprite.getHeight(), 0.001f);
    }
}




