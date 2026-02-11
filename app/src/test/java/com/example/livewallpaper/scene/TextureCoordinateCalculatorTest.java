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
        float width = 120f;
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
}
