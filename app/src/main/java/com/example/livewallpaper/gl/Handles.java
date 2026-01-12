package com.example.livewallpaper.gl;

import android.opengl.GLES20;
import android.util.Log;

public class Handles {
    private static final String TAG = "Handles";

    public int texCoordHandle;
    public int samplerHandle;
    public int projectionMatrixHandle;
    public int scrollOffsetHandle;
    public int parallaxMultiplierHandle;
    public int gyroOffsetXHandle;
    public int gyroOffsetYHandle;
    public int positionHandle;
    public int alphaHandle;

    public Handles(int program)
    {
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        Log.d(TAG, "positionHandle: " + positionHandle);

        texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord");
        Log.d(TAG, "texCoordHandle: " + texCoordHandle);

        samplerHandle = GLES20.glGetUniformLocation(program, "samplerTexture");
        Log.d(TAG, "samplerHandle: " + samplerHandle);

        projectionMatrixHandle = GLES20.glGetUniformLocation(program, "projectionMatrix");
        Log.d(TAG, "projectionMatrixHandle: " + projectionMatrixHandle);

        scrollOffsetHandle = GLES20.glGetUniformLocation(program, "scrollOffset");
        Log.d(TAG, "scrollOffsetHandle: " + scrollOffsetHandle);

        parallaxMultiplierHandle = GLES20.glGetAttribLocation(program, "parallaxMultiplier");
        Log.d(TAG, "parallaxMultiplierHandle: " + parallaxMultiplierHandle);

        gyroOffsetXHandle = GLES20.glGetUniformLocation(program, "gyroOffsetX");
        Log.d(TAG, "gyroOffsetXHandle: " + gyroOffsetXHandle);

        gyroOffsetYHandle = GLES20.glGetUniformLocation(program, "gyroOffsetY");
        Log.d(TAG, "gyroOffsetYHandle: " + gyroOffsetYHandle);

        alphaHandle = GLES20.glGetUniformLocation(program, "alpha");
        Log.d(TAG, "alphaHandle: " + alphaHandle);

        // Check for any -1 values which indicate errors
        if (positionHandle == -1 || texCoordHandle == -1 || samplerHandle == -1) {
            Log.e(TAG, "ERROR: Failed to get critical shader handles!");
        }
    }
}
