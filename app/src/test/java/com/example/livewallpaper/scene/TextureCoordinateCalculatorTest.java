package com.example.livewallpaper.scene;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for TextureCoordinateCalculator.
 * Verifies round-trip conversion: forward calculation + reverse extraction equals original values.
 */
@RunWith(RobolectricTestRunner.class)
public class TextureCoordinateCalculatorTest {

    @Test
    public void testRoundTripConversion_Scale() {
        // Test that we can extract the same scale value after forward calculation
        float originalScale = 2.5f;
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float width = 100f;
        float height = 100f;
        float originalWidth = 100f;
        float originalHeight = 100f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract scale from coordinates (pass sprite dimensions)
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted value should match original
        assertEquals("Extracted scale should match original scale", originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_Scale_NonSquareSprite() {
        // Test with a tall, narrow sprite (height > width)
        float originalScale = 2.0f;
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float width = 50f;
        float height = 150f;  // Much taller than wide
        float originalWidth = 50f;
        float originalHeight = 150f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract scale from coordinates (pass sprite dimensions)
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted value should match original
        assertEquals("Extracted scale should match original scale for non-square sprite",
                originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_Scale_WideSprite() {
        // Test with a wide, short sprite (width > height)
        float originalScale = 3.0f;
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float width = 200f;
        float height = 80f;  // Much wider than tall
        float originalWidth = 200f;
        float originalHeight = 80f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract scale from coordinates (pass sprite dimensions)
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted value should match original
        assertEquals("Extracted scale should match original scale for wide sprite",
                originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_OffsetU() {
        // Test that we can extract the same U offset value after forward calculation
        // Note: offset only works when scale > 1.0 (zoomed in, so window is smaller)
        float scale = 2.0f;  // Changed from 1.0f to allow offset
        float originalOffsetU = 0.1f;
        float offsetV = 0.0f;
        float width = 100f;
        float height = 100f;
        float originalWidth = 100f;
        float originalHeight = 100f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        scale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        originalOffsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract offset from coordinates
        float extractedOffsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(texCoords);

        // Verify: extracted value should match original
        assertEquals("Extracted offsetU should match original offsetU", originalOffsetU, extractedOffsetU, 0.01f);
    }

    @Test
    public void testRoundTripConversion_OffsetV() {
        // Test that we can extract the same V offset value after forward calculation
        // Note: offset only works when scale > 1.0 (zoomed in, so window is smaller)
        float scale = 2.0f;  // Changed from 1.0f to allow offset
        float offsetU = 0.0f;
        float originalOffsetV = -0.1f;
        float width = 100f;
        float height = 100f;
        float originalWidth = 30f;
        float originalHeight = 100f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        scale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, originalOffsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract offset from coordinates
        float extractedOffsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(texCoords);

        // Verify: extracted value should match original
        assertEquals("Extracted offsetV should match original offsetV", originalOffsetV, extractedOffsetV, 0.01f);
    }

    @Test
    public void testRoundTripConversion_AllValues() {
        // Test round-trip conversion with non-zero scale and offsets
        float originalScale = 3.0f;
        float originalOffsetU = 0.15f;
        float originalOffsetV = -0.1f;
        float width = 30f;  // Changed from 120f to match originalWidth
        float height = 100f;
        float originalWidth = 30f;
        float originalHeight = 100f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        originalOffsetU, originalOffsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract all values from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );
        float extractedOffsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(texCoords);
        float extractedOffsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(texCoords);

        // Verify: all extracted values should match originals
        assertEquals("Extracted scale should match original", originalScale, extractedScale, 0.01f);
        assertEquals("Extracted offsetU should match original", originalOffsetU, extractedOffsetU, 0.01f);
        assertEquals("Extracted offsetV should match original", originalOffsetV, extractedOffsetV, 0.01f);
    }

    @Test
    public void testRoundTripConversion_WithClamping() {
        // Test round-trip conversion with values that might be clamped
        float originalScale = 1.5f;
        float originalOffsetU = 0.0f;
        float originalOffsetV = 0.0f;
        float width = 150f;
        float height = 150f;
        float originalWidth = 30f;
        float originalHeight = 100f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        originalOffsetU, originalOffsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Verify that coordinates are within [0, 1] range
        assertTrue("All texture coordinates should be >= 0", texCoords[0] >= 0 && texCoords[2] >= 0 &&
                   texCoords[4] >= 0 && texCoords[6] >= 0);
        assertTrue("All texture coordinates should be <= 1", texCoords[0] <= 1 && texCoords[2] <= 1 &&
                   texCoords[4] <= 1 && texCoords[6] <= 1);

        // Reverse: Extract scale from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted scale should be valid
        assertTrue("Extracted scale should be >= 1.0", extractedScale >= 1.0f);
        assertTrue("Extracted scale should be <= 8.0", extractedScale <= 8.0f);
    }

    @Test
    public void testExtractFromNullCoordinates() {
        // Test that extraction methods handle null gracefully
        float scale = TextureCoordinateCalculator.extractScaleFromCoordinates(null);
        float offsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(null);
        float offsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(null);

        assertEquals("Null scale should return 1.0f", 1.0f, scale, 0.0f);
        assertEquals("Null offsetU should return 0.0f", 0.0f, offsetU, 0.0f);
        assertEquals("Null offsetV should return 0.0f", 0.0f, offsetV, 0.0f);
    }

    @Test
    public void testExtractFromInvalidLength() {
        // Test that extraction methods handle invalid array length
        float[] invalidArray = {0.5f, 0.5f}; // Too short

        float scale = TextureCoordinateCalculator.extractScaleFromCoordinates(invalidArray);
        float offsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(invalidArray);
        float offsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(invalidArray);

        assertEquals("Invalid length scale should return 1.0f", 1.0f, scale, 0.0f);
        assertEquals("Invalid length offsetU should return 0.0f", 0.0f, offsetU, 0.0f);
        assertEquals("Invalid length offsetV should return 0.0f", 0.0f, offsetV, 0.0f);
    }

    // ========== Rectangle Sprite Tests ==========
    // Tests based on real sprite data: Width: 2.0, Height: 6.0 (girlspritesheet example)
    // These tests verify round-trip conversion with non-square sprites

    @Test
    public void testRoundTripConversion_NarrowTallSprite_Scale1_5() {
        // Test with a narrow, tall sprite (height >> width)
        // Based on girlspritesheet: Width: 2.0, Height: 6.0 (ratio 1:3)
        float originalScale = 1.5f;
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float width = 2.0f;
        float height = 6.0f;
        float originalWidth = 2.0f;
        float originalHeight = 6.0f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract scale from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted value should match original
        assertEquals("Narrow tall sprite: Extracted scale should match original",
                originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_NarrowTallSprite_Scale2_0() {
        // Test with narrow, tall sprite and higher scale
        // Based on girlspritesheet: Width: 2.0, Height: 6.0
        float originalScale = 2.0f;
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float width = 2.0f;
        float height = 6.0f;
        float originalWidth = 2.0f;
        float originalHeight = 6.0f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract scale from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted value should match original
        assertEquals("Narrow tall sprite: Extracted scale 2.0 should match original",
                originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_NarrowTallSprite_WithOffsets() {
        // Test narrow tall sprite with both scale and offsets
        // Based on girlspritesheet: Width: 2.0, Height: 6.0
        float originalScale = 2.5f;
        float originalOffsetU = 0.1f;
        float originalOffsetV = -0.15f;
        float width = 2.0f;
        float height = 6.0f;
        float originalWidth = 2.0f;
        float originalHeight = 6.0f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        originalOffsetU, originalOffsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract all values from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );
        float extractedOffsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(texCoords);
        float extractedOffsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(texCoords);

        // Verify: all extracted values should match originals
        assertEquals("Narrow tall: Extracted scale should match original",
                originalScale, extractedScale, 0.01f);
        assertEquals("Narrow tall: Extracted offsetU should match original",
                originalOffsetU, extractedOffsetU, 0.01f);
        assertEquals("Narrow tall: Extracted offsetV should match original",
                originalOffsetV, extractedOffsetV, 0.01f);
    }

    @Test
    public void testRoundTripConversion_AdjustedTexCoords_NarrowTall() {
        // Test with a portion of the texture (adjusted texture coordinates)
        // Simulates a sprite using only part of the texture sheet
        // Based on girlspritesheet with a sub-rectangle of the texture
        float originalScale = 1.8f;
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float width = 2.0f;
        float height = 6.0f;
        float originalWidth = 2.0f;
        float originalHeight = 6.0f;
        float textureScaleFactor = 1.0f;
        // Adjusted coordinates: using only a portion of the texture (left half)
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.5f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract scale from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted value should match original
        assertEquals("Adjusted texCoords narrow tall: Extracted scale should match original",
                originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_AdjustedTexCoords_WithOffsets() {
        // Test with adjusted texture coordinates AND scale + offsets.
        // The adjusted coords have a non-centered UV window (baseCenterU=0.75, baseCenterV=0.6),
        // so extractOffset* functions (which subtract 0.5) measure total-position-from-center,
        // not the stored textureOffsetU/V. The scale round-trip is what we verify here.
        float originalScale = 2.2f;
        float offsetU = 0.08f;
        float offsetV = -0.12f;
        float width = 2.0f;
        float height = 6.0f;
        float originalWidth = 2.0f;
        float originalHeight = 6.0f;
        float textureScaleFactor = 1.0f;
        // Adjusted coordinates: using right half of texture, upper portion
        // initWindowU=0.5, initWindowV=0.8 → minScaleForCoverage=0.8
        float[] originalTexCoordinates = {0.5f, 1.0f, 0.5f, 0.2f, 1.0f, 1.0f, 1.0f, 0.2f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Verify coords are within [0, 1]
        for (float c : texCoords) {
            assertTrue("Texture coordinate should be in [0,1]: " + c, c >= 0f && c <= 1f);
        }

        // Reverse: Extract scale — must round-trip correctly
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );
        assertEquals("Adjusted texCoords: Extracted scale should match original",
                originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_WideRectangle() {
        // Test with a wide, short rectangle sprite
        float originalScale = 1.7f;
        float offsetU = 0.0f;
        float offsetV = 0.0f;
        float width = 8.0f;
        float height = 2.0f;
        float originalWidth = 8.0f;
        float originalHeight = 2.0f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        offsetU, offsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract scale from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );

        // Verify: extracted value should match original
        assertEquals("Wide rectangle: Extracted scale should match original",
                originalScale, extractedScale, 0.01f);
    }

    @Test
    public void testRoundTripConversion_WideRectangle_WithOffsets() {
        // Test wide rectangle with scale and offsets
        float originalScale = 2.3f;
        float originalOffsetU = -0.1f;
        float originalOffsetV = 0.05f;
        float width = 8.0f;
        float height = 2.0f;
        float originalWidth = 8.0f;
        float originalHeight = 2.0f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        originalOffsetU, originalOffsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract all values from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );
        float extractedOffsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(texCoords);
        float extractedOffsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(texCoords);

        // Verify: all extracted values should match originals
        assertEquals("Wide rectangle: Extracted scale should match original",
                originalScale, extractedScale, 0.01f);
        assertEquals("Wide rectangle: Extracted offsetU should match original",
                originalOffsetU, extractedOffsetU, 0.01f);
        assertEquals("Wide rectangle: Extracted offsetV should match original",
                originalOffsetV, extractedOffsetV, 0.01f);
    }

    @Test
    public void testRoundTripConversion_MediumRectangle_Scale3_0() {
        // Test with a more moderate rectangle aspect ratio
        float originalScale = 3.0f;
        float originalOffsetU = 0.05f;
        float originalOffsetV = -0.08f;
        float width = 4.0f;
        float height = 3.0f;
        float originalWidth = 4.0f;
        float originalHeight = 3.0f;
        float textureScaleFactor = 1.0f;
        // Adjusted coordinates: center portion of texture
        float[] originalTexCoordinates = {0.1f, 0.9f, 0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.1f};

        // Forward: Calculate texture coordinates
        TextureCoordinateCalculator.TextureCoordinateData data =
                TextureCoordinateCalculator.calculateTextureCoordinates(
                        originalTexCoordinates,
                        originalScale,
                        width, height,
                        originalWidth, originalHeight,
                        textureScaleFactor,
                        originalOffsetU, originalOffsetV
                );

        // Build the texture coordinate array
        float[] texCoords = TextureCoordinateCalculator.buildTextureCoordinateArray(data);

        // Reverse: Extract all values from coordinates
        float extractedScale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                texCoords,
                originalWidth,
                originalHeight,
                originalTexCoordinates
        );
        float extractedOffsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(texCoords);
        float extractedOffsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(texCoords);

        // Verify: all extracted values should match originals
        assertEquals("Medium rectangle: Extracted scale should match original",
                originalScale, extractedScale, 0.01f);
        assertEquals("Medium rectangle: Extracted offsetU should match original",
                originalOffsetU, extractedOffsetU, 0.01f);
        assertEquals("Medium rectangle: Extracted offsetV should match original",
                originalOffsetV, extractedOffsetV, 0.01f);
    }

    // ========== Texture Offset Clamping Tests ==========
    // Tests for bug fix: texture clamping should account for sprite dimension changes
    // Scenario: User adjusts sprite width via sliders, then tries to drag texture to edges

    @Test
    public void testTextureOffsetClamping_WithDimensionChange_CanReachMinU() {
        // Test case: Sprite grows wider, offset should be able to reach minimum (left edge)
        // Original: width=1.8, height=5.0, scale=2.0, offsetU=0.22 (zoomed in, offset visible)
        // After resize: width=5.6 (3.1x larger)
        // Should be able to drag texture to reach uMin=0

        float originalWidth = 1.8f;
        float originalHeight = 5.0f;
        float currentWidth = 5.6f;  // Grown from original
        float currentHeight = 5.0f;  // Unchanged
        float textureScale = 2.0f;
        float currentOffsetU = 0.22f;
        float currentOffsetV = 0.25f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Try to move texture all the way left (maximum negative delta)
        float deltaU = -0.5f;  // Large negative delta
        float deltaV = 0.0f;

        float[] clampedOffsets = TextureCoordinateCalculator.clampTextureOffset(
                currentOffsetU, currentOffsetV,
                deltaU, deltaV,
                currentWidth, currentHeight,
                originalWidth, originalHeight,
                textureScale,
                textureScaleFactor,
                originalTexCoordinates
        );

        // With the larger sprite width, the window should be bigger, allowing offset to reach minimum
        float minPossibleOffsetU = clampedOffsets[0];

        // minOffsetU = halfWindowU - 0.5, where halfWindowU = windowSizeU * 0.5
        // windowSizeU should be larger due to widthGrowthFactor = 5.6/1.8 = 3.11
        // This means minOffsetU should be negative (allowing left edge positioning)
        assertTrue("With wider sprite, offsetU should be able to reach negative values for left edge",
                minPossibleOffsetU < 0.1f);
    }

    @Test
    public void testTextureOffsetClamping_WithDimensionChange_CanReachMaxU() {
        // Test case: Sprite grows wider, offset should be able to reach maximum (right edge)
        // Original: width=1.8, height=5.0, scale=2.0
        // After resize: width=5.6 (3.1x larger)
        // Should be able to drag texture all the way right (maximum positive offset)

        float originalWidth = 1.8f;
        float originalHeight = 5.0f;
        float currentWidth = 5.6f;  // Grown from original
        float currentHeight = 5.0f;  // Unchanged
        float textureScale = 2.0f;
        float currentOffsetU = -0.1f;
        float currentOffsetV = 0.25f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        // Try to move texture all the way right (maximum positive delta)
        float deltaU = 0.5f;  // Large positive delta
        float deltaV = 0.0f;

        float[] clampedOffsets = TextureCoordinateCalculator.clampTextureOffset(
                currentOffsetU, currentOffsetV,
                deltaU, deltaV,
                currentWidth, currentHeight,
                originalWidth, originalHeight,
                textureScale,
                textureScaleFactor,
                originalTexCoordinates
        );

        // With the larger sprite width, maxOffsetU should be more positive
        float maxPossibleOffsetU = clampedOffsets[0];

        // maxOffsetU = 0.5 - halfWindowU
        // With larger window, this should still allow positive offsets
        assertTrue("With wider sprite, offsetU should be able to reach positive values for right edge",
                maxPossibleOffsetU > -0.5f);
    }

    @Test
    public void testTextureOffsetClamping_MultipleResizes() {
        // Test case: Verify that texture panning works when sprite dimensions change significantly.
        // With the relative-scale model (effectiveScale = minScaleForCoverage * textureScale),
        // textureScale=2.0 on a 5.6-wide sprite against a 1.8-wide natural texture gives:
        //   minScaleForCoverage = 5.6/1.8 ≈ 3.11
        //   effectiveScale ≈ 6.22  →  windowSizeU = 5.6/(1.8·6.22) = 0.5
        //   halfU = 0.25  →  max offset = ±0.25 from base center

        float originalWidth = 1.8f;
        float originalHeight = 5.0f;
        float textureScale = 2.0f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        float largeWidth = 5.6f;

        // Try to move texture far to the left
        float[] clampedOffsetsLeft = TextureCoordinateCalculator.clampTextureOffset(
                0.0f,  // Start at center
                0.25f,
                -0.6f,  // Request large leftward movement
                0.0f,
                largeWidth,
                originalHeight,
                originalWidth,
                originalHeight,
                textureScale,
                textureScaleFactor,
                originalTexCoordinates
        );

        // With textureScale=2.0 above minimum, the window is 0.5 wide → max pan ≈ -0.25
        assertTrue("Should allow texture movement leftward when sprite is wide",
                clampedOffsetsLeft[0] <= -0.2f);

        // Try to move texture far to the right
        float[] clampedOffsetsRight = TextureCoordinateCalculator.clampTextureOffset(
                0.0f,
                0.25f,
                0.6f,  // Request large rightward movement
                0.0f,
                largeWidth,
                originalHeight,
                originalWidth,
                originalHeight,
                textureScale,
                textureScaleFactor,
                originalTexCoordinates
        );

        assertTrue("Should allow texture movement rightward when sprite is wide",
                clampedOffsetsRight[0] >= 0.2f);
    }

    @Test
    public void testTextureOffsetClamping_AspectRatioDuringResize() {
        // Test case: Verify aspect ratio handling during sprite resize
        // When sprite grows, texture window should adapt while maintaining aspect ratio

        float originalWidth = 1.8f;
        float originalHeight = 5.0f;
        float currentWidth = 4.6f;  // Significantly wider
        float currentHeight = 5.0f;
        float textureScale = 2.0f;
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        float[] clampedOffsets = TextureCoordinateCalculator.clampTextureOffset(
                0.2f,
                0.25f,
                0.1f,  // Some movement
                0.1f,
                currentWidth, currentHeight,
                originalWidth, originalHeight,
                textureScale,
                textureScaleFactor,
                originalTexCoordinates
        );

        // Offsets should be valid (not extreme)
        assertTrue("Offset U should be reasonable", clampedOffsets[0] >= -0.5f && clampedOffsets[0] <= 0.5f);
        assertTrue("Offset V should be reasonable", clampedOffsets[1] >= -0.5f && clampedOffsets[1] <= 0.5f);
    }

    @Test
    public void testTextureOffsetClamping_NoMovementConstraintWithoutZoom() {
        // Test case: With scale=1.0 (no zoom), window fills entire texture
        // Verify offsets are properly clamped to valid range

        float originalWidth = 1.8f;
        float originalHeight = 5.0f;
        float currentWidth = 5.6f;
        float currentHeight = 5.0f;
        float textureScale = 1.0f;  // No zoom, full texture visible
        float textureScaleFactor = 1.0f;
        float[] originalTexCoordinates = {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f};

        float[] clampedOffsets = TextureCoordinateCalculator.clampTextureOffset(
                0.0f,
                0.0f,
                1.0f,  // Try huge delta
                1.0f,
                currentWidth, currentHeight,
                originalWidth, originalHeight,
                textureScale,
                textureScaleFactor,
                originalTexCoordinates
        );

        // Offsets must be within valid range [-0.5, 0.5]
        assertTrue("offsetU must be in valid range",
                clampedOffsets[0] >= -0.5f && clampedOffsets[0] <= 0.5f);
        assertTrue("offsetV must be in valid range",
                clampedOffsets[1] >= -0.5f && clampedOffsets[1] <= 0.5f);
    }
}
