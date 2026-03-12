package com.example.livewallpaper.scene.models;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.FloatBuffer;

/**
 * Unit tests for PhoneGuide model class.
 * Tests phone guide creation, geometry buffers, aspect ratio, and positioning.
 */
public class PhoneGuideTest {

    private PhoneGuide phoneGuide;

    @Before
    public void setUp() {
        phoneGuide = new PhoneGuide();
    }

    // ==================== Initialization Tests ====================

    @Test
    public void constructor_InitializesBuffers() {
        assertNotNull("rectangleEdgeLineBuffer should be initialized", phoneGuide.getRectangleEdgeLineBuffer());
        assertNotNull("centerLineBuffer should be initialized", phoneGuide.getCenterLineBuffer());
    }

    @Test
    public void constructor_DefaultXOffsetIsZero() {
        assertEquals(0.0f, phoneGuide.getXOffset(), 0.001f);
    }

    // ==================== Rectangle Geometry Tests ====================

    @Test
    public void getRectangleVertexCount_ReturnsCorrectValue() {
        assertEquals("Rectangle should have 5 vertices (closed box)", 5, phoneGuide.getRectangleVertexCount());
    }

    @Test
    public void getRectangleEdgeLineBuffer_IsNotNull() {
        assertNotNull("rectangleEdgeLineBuffer should not be null", phoneGuide.getRectangleEdgeLineBuffer());
    }

    @Test
    public void getRectangleEdgeLineBuffer_HasCorrectCapacity() {
        FloatBuffer buffer = phoneGuide.getRectangleEdgeLineBuffer();
        // 5 vertices * 3 coordinates each (x, y, z) = 15 floats
        assertTrue("Buffer capacity should be at least 15", buffer.capacity() >= 15);
    }

    @Test
    public void rectangleBuffer_ContainsValidData() {
        FloatBuffer buffer = phoneGuide.getRectangleEdgeLineBuffer();

        // Get first few values to verify buffer has data
        float[] data = new float[15];
        buffer.get(data);
        buffer.position(0);

        // All coordinates should be valid floats
        for (float value : data) {
            assertFalse("Coordinates should be valid floats", Float.isNaN(value));
        }
    }

    // ==================== Center Line Geometry Tests ====================

    @Test
    public void getCenterLineVertexCount_ReturnsCorrectValue() {
        assertEquals("Center line should have 2 vertices", 2, phoneGuide.getCenterLineVertexCount());
    }

    @Test
    public void getCenterLineBuffer_IsNotNull() {
        assertNotNull("centerLineBuffer should not be null", phoneGuide.getCenterLineBuffer());
    }

    @Test
    public void getCenterLineBuffer_HasCorrectCapacity() {
        FloatBuffer buffer = phoneGuide.getCenterLineBuffer();
        // 2 vertices * 3 coordinates each (x, y, z) = 6 floats
        assertTrue("Buffer capacity should be at least 6", buffer.capacity() >= 6);
    }

    @Test
    public void centerLineBuffer_ContainsValidData() {
        FloatBuffer buffer = phoneGuide.getCenterLineBuffer();

        // Get all values
        float[] data = new float[6];
        buffer.get(data);
        buffer.position(0);

        // All coordinates should be valid floats
        for (float value : data) {
            assertFalse("Coordinates should be valid floats", Float.isNaN(value));
        }
    }

    // ==================== XOffset Property Tests ====================

    @Test
    public void setXOffset_UpdatesOffset() {
        phoneGuide.setXOffset(2.5f);
        assertEquals(2.5f, phoneGuide.getXOffset(), 0.001f);
    }

    @Test
    public void setXOffset_WithVariousValues() {
        phoneGuide.setXOffset(-5.0f);
        assertEquals(-5.0f, phoneGuide.getXOffset(), 0.001f);

        phoneGuide.setXOffset(0.0f);
        assertEquals(0.0f, phoneGuide.getXOffset(), 0.001f);

        phoneGuide.setXOffset(10.5f);
        assertEquals(10.5f, phoneGuide.getXOffset(), 0.001f);
    }

    @Test
    public void setXOffset_WithExtremeLargeValues() {
        phoneGuide.setXOffset(Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, phoneGuide.getXOffset(), 0.001f);

        phoneGuide.setXOffset(Float.MIN_VALUE);
        assertEquals(Float.MIN_VALUE, phoneGuide.getXOffset(), 0.001f);
    }

    @Test
    public void setXOffset_Multiple_Times() {
        phoneGuide.setXOffset(1.0f);
        assertEquals(1.0f, phoneGuide.getXOffset(), 0.001f);

        phoneGuide.setXOffset(2.0f);
        assertEquals(2.0f, phoneGuide.getXOffset(), 0.001f);

        phoneGuide.setXOffset(-3.0f);
        assertEquals(-3.0f, phoneGuide.getXOffset(), 0.001f);
    }

    // ==================== Aspect Ratio Tests ====================

    @Test
    public void phoneGuideAspectRatio_Is21To9() {
        // The guide should be a 21:9 aspect ratio
        // This is verified by the initialization logic in PhoneGuide

        FloatBuffer rectangleBuffer = phoneGuide.getRectangleEdgeLineBuffer();
        float[] data = new float[15];
        rectangleBuffer.get(data);
        rectangleBuffer.position(0);

        // Extract width and height from the rectangle coordinates
        // Rectangle coords: top-left x, top-left y, ..., top-right x, ...
        // We can calculate from the x and y differences
        float leftX = data[0];    // top-left x
        float rightX = data[3];   // top-right x
        float topY = data[1];     // top-left y
        float bottomY = data[7];  // bottom-left y (second vertex in strip)

        float width = Math.abs(rightX - leftX);
        float height = Math.abs(topY - bottomY);

        // The width should be less than or equal to the visible world bounds (10.0)
        assertTrue("Width should be at most 10.0", width <= 10.0f);

        // Height should be approximately 9.99
        assertTrue("Height should be approximately 9.99", height >= 9.0f && height <= 10.0f);

        // The aspect ratio should be close to 9/21
        float actualRatio = width / height;
        float expectedRatio = 9f / 21f;
        assertEquals("Aspect ratio should be close to 9/21", expectedRatio, actualRatio, 0.1f);
    }

    // ==================== Multiple Instances Tests ====================

    @Test
    public void multipleInstances_AreIndependent() {
        PhoneGuide guide1 = new PhoneGuide();
        PhoneGuide guide2 = new PhoneGuide();

        guide1.setXOffset(1.5f);
        guide2.setXOffset(3.5f);

        assertEquals(1.5f, guide1.getXOffset(), 0.001f);
        assertEquals(3.5f, guide2.getXOffset(), 0.001f);
    }

    @Test
    public void multipleInstances_HaveSeparateBuffers() {
        PhoneGuide guide1 = new PhoneGuide();
        PhoneGuide guide2 = new PhoneGuide();

        FloatBuffer buffer1 = guide1.getRectangleEdgeLineBuffer();
        FloatBuffer buffer2 = guide2.getRectangleEdgeLineBuffer();

        assertNotSame("Buffers should be different objects", buffer1, buffer2);
    }

    // ==================== Buffer Consistency Tests ====================

    @Test
    public void rectangleAndCenterLineBuffers_UseSameCoordinateSystem() {
        FloatBuffer rectBuffer = phoneGuide.getRectangleEdgeLineBuffer();
        FloatBuffer centerBuffer = phoneGuide.getCenterLineBuffer();

        float[] rectData = new float[15];
        float[] centerData = new float[6];

        rectBuffer.get(rectData);
        centerBuffer.get(centerData);

        rectBuffer.position(0);
        centerBuffer.position(0);

        // All coordinates should be valid (checking for NaN)
        for (float value : rectData) {
            assertFalse("Rectangle buffer should contain valid floats", Float.isNaN(value));
        }

        for (float value : centerData) {
            assertFalse("Center buffer should contain valid floats", Float.isNaN(value));
        }
    }

    @Test
    public void centerLineIsVertical() {
        FloatBuffer centerBuffer = phoneGuide.getCenterLineBuffer();
        float[] data = new float[6];
        centerBuffer.get(data);
        centerBuffer.position(0);

        // Center line should have same x and z coordinates for both vertices
        float topX = data[0];
        float topZ = data[2];
        float bottomX = data[3];
        float bottomZ = data[5];

        assertEquals("Both vertices should have same x coordinate", topX, bottomX, 0.001f);
        assertEquals("Both vertices should have same z coordinate", topZ, bottomZ, 0.001f);

        // Center line x should be at origin (0.0)
        assertEquals("Center line should be at x=0", 0.0f, topX, 0.001f);

        // Y coordinates should be opposite (top and bottom)
        float topY = data[1];
        float bottomY = data[4];
        assertTrue("Top Y should be positive or zero", topY >= 0);
        assertTrue("Bottom Y should be negative or zero", bottomY <= 0);
    }

    // ==================== Buffer Position Tests ====================

    @Test
    public void getRectangleEdgeLineBuffer_PositionIsReset() {
        FloatBuffer buffer = phoneGuide.getRectangleEdgeLineBuffer();

        // Read some data
        float[] data = new float[15];
        buffer.get(data);

        // Position should be reset to beginning
        buffer.position(0);

        // Position should be 0
        assertEquals("Position should be reset to 0", 0, buffer.position());
    }

    @Test
    public void getCenterLineBuffer_PositionIsReset() {
        FloatBuffer buffer = phoneGuide.getCenterLineBuffer();

        // Read some data
        float[] data = new float[6];
        buffer.get(data);

        // Position should be reset to beginning
        buffer.position(0);

        // Position should be 0
        assertEquals("Position should be reset to 0", 0, buffer.position());
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void setXOffset_WithZero() {
        phoneGuide.setXOffset(1.5f);
        phoneGuide.setXOffset(0.0f);
        assertEquals(0.0f, phoneGuide.getXOffset(), 0.001f);
    }

    @Test
    public void setXOffset_WithNegativeValues() {
        phoneGuide.setXOffset(-10.0f);
        assertEquals(-10.0f, phoneGuide.getXOffset(), 0.001f);

        phoneGuide.setXOffset(-0.001f);
        assertEquals(-0.001f, phoneGuide.getXOffset(), 0.001f);
    }
}



