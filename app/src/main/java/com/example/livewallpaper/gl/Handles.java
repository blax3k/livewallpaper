package com.example.livewallpaper.gl;

import android.opengl.GLES20;

public class Handles {

    public int texCoordHandle;
    public int samplerHandle;
    public int projectionMatrixHandle;
    public int scrollOffsetHandle;
    public int parallaxMultiplierHandle;
    public int gyroOffsetXHandle;
    public int gyroOffsetYHandle;
    public int positionHandle;

    public Handles(int program)
    {
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord");
        samplerHandle = GLES20.glGetUniformLocation(program, "samplerTexture");
        projectionMatrixHandle = GLES20.glGetUniformLocation(program, "projectionMatrix");
        scrollOffsetHandle = GLES20.glGetUniformLocation(program, "scrollOffset");
        parallaxMultiplierHandle = GLES20.glGetAttribLocation(program, "parallaxMultiplier");
        gyroOffsetXHandle = GLES20.glGetUniformLocation(program, "gyroOffsetX");
        gyroOffsetYHandle = GLES20.glGetUniformLocation(program, "gyroOffsetY");
    }
}
