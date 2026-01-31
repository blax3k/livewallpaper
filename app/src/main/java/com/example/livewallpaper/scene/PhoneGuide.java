package com.example.livewallpaper.scene;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Represents a 21:9 aspect ratio guide with a vertical center line.
 * Used in the EditSceneActivity to show the safe area for phone displays.
 * This shape is unaffected by gyroscope motion.
 * The rectangle's top and bottom edges meet the glView edges.
 */
public class PhoneGuide {
    private static final String TAG = "PhoneGuide";
    private static final float ASPECT_RATIO = 9f/21f;  // 21:9
    private static final float WORLD_HEIGHT = 10f;  // Match the world height in ScenePreviewRenderer
    private static final float GUIDE_HEIGHT = 9.99f;  // Slightly smaller than world height to ensure it fits within visible bounds

    private FloatBuffer rectangleEdgeLineBuffer;
    private FloatBuffer centerLineBuffer;
    private int rectangleVertexCount = 5;  // 5 vertices for closed box (line strip)
    private int centerLineVertexCount = 2;  // 2 vertices for center vertical line

    public PhoneGuide() {
        initializeGeometry();
    }

    /**
     * Initialize the geometry buffers for the 21:9 rectangle and center line.
     * Takes into account that the viewport is square (1:1 aspect ratio).
     */
    private void initializeGeometry() {
        // The viewport is square, so the visible world goes from -5 to +5 in both X and Y
        float visibleWorldBound = WORLD_HEIGHT;  // = 5.0

        // For a 21:9 phone on a square viewport, we need to constrain the width
        // to fit within the visible bounds
        float height = GUIDE_HEIGHT;
        float width = height * ASPECT_RATIO;  // 21:9 ratio

        // Clamp the width to the visible world bounds
        float maxWidth = visibleWorldBound * 2f;  // = 10.0 (from -5 to +5)
        if (width > maxWidth) {
            width = maxWidth;
            height = width / ASPECT_RATIO;  // Recalculate height to maintain aspect ratio
        }

        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        // Rectangle outline (5 vertices to draw a closed box)
        float[] rectangleCoords = {
            -halfWidth,   halfHeight, 0.0f,  // top left
             halfWidth,   halfHeight, 0.0f,  // top right
             halfWidth,  -halfHeight, 0.0f,  // bottom right
            -halfWidth,  -halfHeight, 0.0f,  // bottom left
            -halfWidth,   halfHeight, 0.0f   // back to top left
        };

        ByteBuffer rbb = ByteBuffer.allocateDirect(rectangleCoords.length * 4);
        rbb.order(ByteOrder.nativeOrder());
        rectangleEdgeLineBuffer = rbb.asFloatBuffer();
        rectangleEdgeLineBuffer.put(rectangleCoords);
        rectangleEdgeLineBuffer.position(0);
        rectangleVertexCount = 5;

        // Center vertical line (2 vertices) - runs from top to bottom through center
        float[] centerLineCoords = {
            0.0f,   halfHeight, 0.0f,  // top point on center line
            0.0f,  -halfHeight, 0.0f   // bottom point on center line
        };

        ByteBuffer clb = ByteBuffer.allocateDirect(centerLineCoords.length * 4);
        clb.order(ByteOrder.nativeOrder());
        centerLineBuffer = clb.asFloatBuffer();
        centerLineBuffer.put(centerLineCoords);
        centerLineBuffer.position(0);
        centerLineVertexCount = 2;
    }

    /**
     * Get the buffer containing the rectangle edge line vertices.
     */
    public FloatBuffer getRectangleEdgeLineBuffer() {
        return rectangleEdgeLineBuffer;
    }

    /**
     * Get the vertex count for the rectangle edge line.
     */
    public int getRectangleVertexCount() {
        return rectangleVertexCount;
    }

    /**
     * Get the buffer containing the center line vertices.
     */
    public FloatBuffer getCenterLineBuffer() {
        return centerLineBuffer;
    }

    /**
     * Get the vertex count for the center line.
     */
    public int getCenterLineVertexCount() {
        return centerLineVertexCount;
    }
}
