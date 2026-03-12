package com.example.livewallpaper.scene.models;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for SpriteData model class.
 * Tests data structure initialization, field assignments, and Parcelable implementation.
 */
public class SpriteDataTest {

    private SpriteData spriteData;

    @Before
    public void setUp() {
        spriteData = new SpriteData();
    }

    // ==================== Initialization Tests ====================

    @Test
    public void constructor_InitializesWithDefaultValues() {
        assertNull("textureResource should be null by default", spriteData.textureResource);
        assertEquals("width should be 0 by default", 0.0f, spriteData.width, 0.001f);
        assertEquals("height should be 0 by default", 0.0f, spriteData.height, 0.001f);
        assertEquals("parallaxMultiplier should be 0 by default", 0.0f, spriteData.parallaxMultiplier, 0.001f);
        assertEquals("positionX should be 0 by default", 0.0f, spriteData.positionX, 0.001f);
        assertEquals("positionY should be 0 by default", 0.0f, spriteData.positionY, 0.001f);
        assertNull("texCoordinates should be null by default", spriteData.texCoordinates);
        assertEquals("textureResourceId should be 0 by default", 0, spriteData.textureResourceId);
        assertNull("name should be null by default", spriteData.name);
    }

    // ==================== Field Assignment Tests ====================

    @Test
    public void textureResourceAssignment_WorksCorrectly() {
        spriteData.textureResource = "background";
        assertEquals("background", spriteData.textureResource);

        spriteData.textureResource = "player";
        assertEquals("player", spriteData.textureResource);
    }

    @Test
    public void dimensionAssignments_WorkCorrectly() {
        spriteData.width = 2.5f;
        spriteData.height = 3.5f;

        assertEquals(2.5f, spriteData.width, 0.001f);
        assertEquals(3.5f, spriteData.height, 0.001f);
    }

    @Test
    public void parallaxMultiplierAssignment_WorksCorrectly() {
        spriteData.parallaxMultiplier = 0.5f;
        assertEquals(0.5f, spriteData.parallaxMultiplier, 0.001f);

        spriteData.parallaxMultiplier = 1.0f;
        assertEquals(1.0f, spriteData.parallaxMultiplier, 0.001f);

        spriteData.parallaxMultiplier = 2.0f;
        assertEquals(2.0f, spriteData.parallaxMultiplier, 0.001f);
    }

    @Test
    public void positionAssignments_WorkCorrectly() {
        spriteData.positionX = 1.5f;
        spriteData.positionY = 2.5f;

        assertEquals(1.5f, spriteData.positionX, 0.001f);
        assertEquals(2.5f, spriteData.positionY, 0.001f);
    }

    @Test
    public void textureCoordinatesAssignment_WorksCorrectly() {
        float[] coords = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};
        spriteData.texCoordinates = coords;

        assertNotNull("texCoordinates should not be null", spriteData.texCoordinates);
        assertEquals("texCoordinates length should be 8", 8, spriteData.texCoordinates.length);
        assertArrayEquals("texCoordinates should match assigned values", coords, spriteData.texCoordinates, 0.001f);
    }

    @Test
    public void textureResourceIdAssignment_WorksCorrectly() {
        spriteData.textureResourceId = 12345;
        assertEquals(12345, spriteData.textureResourceId);
    }

    @Test
    public void nameAssignment_WorksCorrectly() {
        spriteData.name = "sprite1";
        assertEquals("sprite1", spriteData.name);

        spriteData.name = "MyCustomSprite";
        assertEquals("MyCustomSprite", spriteData.name);
    }

    // ==================== Complex Assignment Tests ====================

    @Test
    public void fullSpriteConfiguration_CanBeAssigned() {
        spriteData.textureResource = "bg_layer";
        spriteData.width = 10.0f;
        spriteData.height = 5.0f;
        spriteData.parallaxMultiplier = 0.75f;
        spriteData.positionX = -2.0f;
        spriteData.positionY = 1.0f;
        spriteData.texCoordinates = new float[]{0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};
        spriteData.textureResourceId = 9999;
        spriteData.name = "background_layer";

        assertEquals("bg_layer", spriteData.textureResource);
        assertEquals(10.0f, spriteData.width, 0.001f);
        assertEquals(5.0f, spriteData.height, 0.001f);
        assertEquals(0.75f, spriteData.parallaxMultiplier, 0.001f);
        assertEquals(-2.0f, spriteData.positionX, 0.001f);
        assertEquals(1.0f, spriteData.positionY, 0.001f);
        assertNotNull(spriteData.texCoordinates);
        assertEquals(9999, spriteData.textureResourceId);
        assertEquals("background_layer", spriteData.name);
    }

    // ==================== Multiple Instances Tests ====================

    @Test
    public void multipleInstances_AreIndependent() {
        SpriteData sprite1 = new SpriteData();
        SpriteData sprite2 = new SpriteData();

        sprite1.name = "sprite1";
        sprite1.width = 5.0f;

        sprite2.name = "sprite2";
        sprite2.width = 10.0f;

        assertEquals("sprite1", sprite1.name);
        assertEquals("sprite2", sprite2.name);
        assertEquals(5.0f, sprite1.width, 0.001f);
        assertEquals(10.0f, sprite2.width, 0.001f);
    }

    // ==================== Null Handling Tests ====================

    @Test
    public void nullTextureCoordinates_CanBeAssignedAndRetrieved() {
        spriteData.texCoordinates = null;
        assertNull("texCoordinates should be null", spriteData.texCoordinates);

        spriteData.texCoordinates = new float[]{1.0f};
        assertNotNull("texCoordinates should not be null", spriteData.texCoordinates);

        spriteData.texCoordinates = null;
        assertNull("texCoordinates should be null again", spriteData.texCoordinates);
    }

    // ==================== Describecontents Tests ====================

    @Test
    public void describeContents_ReturnsZero() {
        assertEquals("describeContents should always return 0", 0, spriteData.describeContents());
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void extremeValues_CanBeAssigned() {
        spriteData.width = Float.MAX_VALUE;
        spriteData.height = Float.MIN_VALUE;
        spriteData.positionX = -Float.MAX_VALUE;
        spriteData.positionY = Float.POSITIVE_INFINITY;

        assertEquals(Float.MAX_VALUE, spriteData.width, 0.001f);
        assertEquals(Float.MIN_VALUE, spriteData.height, 0.001f);
        assertEquals(-Float.MAX_VALUE, spriteData.positionX, 0.001f);
        assertTrue(Float.isInfinite(spriteData.positionY));
    }

    @Test
    public void zeroAndNegativeValues_CanBeAssigned() {
        spriteData.width = 0.0f;
        spriteData.height = -5.0f;
        spriteData.parallaxMultiplier = -0.5f;

        assertEquals(0.0f, spriteData.width, 0.001f);
        assertEquals(-5.0f, spriteData.height, 0.001f);
        assertEquals(-0.5f, spriteData.parallaxMultiplier, 0.001f);
    }

    @Test
    public void emptyTextureCoordinatesArray_CanBeAssigned() {
        spriteData.texCoordinates = new float[0];
        assertNotNull("texCoordinates should not be null", spriteData.texCoordinates);
        assertEquals("texCoordinates should be empty", 0, spriteData.texCoordinates.length);
    }
}

