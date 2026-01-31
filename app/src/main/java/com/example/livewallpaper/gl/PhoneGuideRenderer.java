package com.example.livewallpaper.gl;

import android.opengl.GLES20;
import com.example.livewallpaper.scene.PhoneGuide;

/**
 * Responsible for rendering the phone guide (21:9 rectangle with center line).
 * Uses the same edge highlighting code as sprites but is unaffected by gyroscope motion.
 */
public class PhoneGuideRenderer {
    private static final String TAG = "PhoneGuideRenderer";
    private final int positionHandle;
    private final int overrideColorHandle;
    private final int useColorOverrideHandle;

    public PhoneGuideRenderer(Handles handles) {
        this.positionHandle = handles.positionHandle;
        this.overrideColorHandle = handles.overrideColorHandle;
        this.useColorOverrideHandle = handles.useColorOverrideHandle;
    }

    /**
     * Draw the phone guide (21:9 rectangle and center line).
     * Uses bright cyan color for the rectangle and bright yellow for the center line.
     */
    public void drawPhoneGuide(PhoneGuide phoneGuide) {
        if (phoneGuide == null) {
            return;
        }

        // Set line width for better visibility
        GLES20.glLineWidth(2.0f);

        // Enable color override
        GLES20.glUniform1f(useColorOverrideHandle, 1.0f);

        // Disable blending for solid colors
        GLES20.glDisable(GLES20.GL_BLEND);

        // Draw rectangle outline in bright cyan (R=0, G=1, B=1, A=1)
        GLES20.glUniform4f(overrideColorHandle, 0.0f, 1.0f, 1.0f, 1.0f);
        enableVertexAttribute(positionHandle, 3, 12, phoneGuide.getRectangleEdgeLineBuffer());
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, phoneGuide.getRectangleVertexCount());
        GLES20.glDisableVertexAttribArray(positionHandle);

        // Draw center horizontal line in bright yellow (R=1, G=1, B=0, A=1)
        GLES20.glUniform4f(overrideColorHandle, 1.0f, 1.0f, 0.0f, 1.0f);
        enableVertexAttribute(positionHandle, 3, 12, phoneGuide.getCenterLineBuffer());
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, phoneGuide.getCenterLineVertexCount());
        GLES20.glDisableVertexAttribArray(positionHandle);

        // Clean up
        GLES20.glLineWidth(1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);

        // Disable color override for next objects
        GLES20.glUniform1f(useColorOverrideHandle, 0.0f);
    }

    /**
     * Helper method to enable a vertex attribute and set its pointer.
     * @param handle the attribute handle
     * @param size number of components per attribute (e.g., 3 for position)
     * @param stride byte offset between consecutive attributes
     * @param buffer the buffer containing the attribute data
     */
    private void enableVertexAttribute(int handle, int size, int stride, java.nio.Buffer buffer) {
        GLES20.glEnableVertexAttribArray(handle);
        GLES20.glVertexAttribPointer(handle, size, GLES20.GL_FLOAT, false, stride, buffer);
    }
}
