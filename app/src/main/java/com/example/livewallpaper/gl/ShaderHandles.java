package com.example.livewallpaper.gl;

import android.opengl.GLES20;

/**
 * Container for shader attribute/uniform handles for a program.
 * Construct with the linked program id and it will query locations.
 */
public class ShaderHandles {
    public final int position;
    public final int texCoord;
    public final int sampler;
    public final int projectionMatrix;
    public final int scrollOffset;
    public final int parallaxMultiplier;
    public final int gyroOffsetX;
    public final int gyroOffsetY;

    public ShaderHandles(int program) {
        this.position = GLES20.glGetAttribLocation(program, "vPosition");
        this.texCoord = GLES20.glGetAttribLocation(program, "vTexCoord");
        this.sampler = GLES20.glGetUniformLocation(program, "samplerTexture");
        this.projectionMatrix = GLES20.glGetUniformLocation(program, "projectionMatrix");
        this.scrollOffset = GLES20.glGetUniformLocation(program, "scrollOffset");
        this.parallaxMultiplier = GLES20.glGetAttribLocation(program, "parallaxMultiplier");
        this.gyroOffsetX = GLES20.glGetUniformLocation(program, "gyroOffsetX");
        this.gyroOffsetY = GLES20.glGetUniformLocation(program, "gyroOffsetY");
    }
}

