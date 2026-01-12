package com.example.livewallpaper.gl;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Lightweight helper to compile/link a GL ES 2.0 shader program and provide helper accessors.
 */
public class ShaderProgram {
    private static final String TAG = "ShaderProgram";
    private int program = 0;

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        program = GLES20.glCreateProgram();
        if (program == 0) {
            Log.e(TAG, "Failed to create GL program");
            return;
        }

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        // Check link status
        final int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String info = GLES20.glGetProgramInfoLog(program);
            Log.e(TAG, "Error linking program: " + info);
            GLES20.glDeleteProgram(program);
            program = 0;
        }

        // We can delete shaders once linked; driver keeps program binary
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
    }

    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader");
            return 0;
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String info = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, "Error compiling shader: " + info);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    public void use() {
        if (program != 0) {
            GLES20.glUseProgram(program);
        }
    }

    public int getProgram() {
        return program;
    }

    public void delete() {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }
}

