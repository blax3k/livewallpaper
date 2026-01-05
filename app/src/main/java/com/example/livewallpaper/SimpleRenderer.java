package com.example.livewallpaper;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Simple example renderer that clears the screen with a changing color.
 */
public class SimpleRenderer implements GLWallpaperRenderer {
    private static final String TAG = "SimpleRenderer";
    private float time = 0f;

    @Override
    public void onSurfaceCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        Log.d(TAG, "Surface created");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame() {
        time += 0.016f;

        // Create a smoothly animated color using sine waves
        float r = (0.5f + 0.5f * (float) Math.sin(time));
        float g = (0.5f + 0.5f * (float) Math.sin(time + 2f));
        float b = (0.5f + 0.5f * (float) Math.sin(time + 4f));

        // Clamp values to [0, 1]
        r = Math.max(0f, Math.min(1f, r));
        g = Math.max(0f, Math.min(1f, g));
        b = Math.max(0f, Math.min(1f, b));

        GLES20.glClearColor(r, g, b, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Renderer destroyed");
    }
}

