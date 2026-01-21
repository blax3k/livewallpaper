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

    public static String getVertexShaderCode() {
        return "uniform mat4 projectionMatrix;"
                + "uniform float scrollOffset;"
                + "uniform float gyroOffsetX;"
                + "uniform float gyroOffsetY;"
                + "attribute vec4 vPosition;"
                + "attribute vec2 vTexCoord;"
                + "attribute float parallaxMultiplier;"
                + "varying vec2 texCoord;"

                + "void main() {"
                + "  vec4 position = vPosition;"
                + "  position.x += scrollOffset * parallaxMultiplier + gyroOffsetX * parallaxMultiplier;"
                + "  position.y += gyroOffsetY * parallaxMultiplier;"
                + "  gl_Position = projectionMatrix * position;"
                + "  texCoord = vTexCoord;"
                + "}";
    }

    public static String getFragmentShaderCode() {
        // wipeProgress: 0.0 to 1.0 transition progress
        // wipeDirection: 1.0 = fade out (wipe erases from top-left to bottom-right)
        //               -1.0 = fade in (wipe reveals from top-left to bottom-right)
        //                0.0 = no wipe effect (fully visible)
        return "precision mediump float;"
                + "uniform sampler2D samplerTexture;"
                + "uniform float wipeProgress;"
                + "uniform float wipeDirection;"
                + "varying vec2 texCoord;"
                + "void main() {"
                + "  vec4 texColor = texture2D(samplerTexture, texCoord);"
                // Calculate diagonal position: 0.0 at top-left, 1.0 at bottom-right
                // texCoord: x goes 0->1 left to right, y goes 0->1 bottom to top
                // So top-left is (0,1), bottom-right is (1,0)
                // diagonal = (x + (1-y)) / 2 gives us 0 at top-left, 1 at bottom-right
                + "  float diagonal = (texCoord.x + (1.0 - texCoord.y)) / 2.0;"
                + "  float alpha = 1.0;"
                + "  float softness = 0.4;"
                + "  if (wipeDirection > 0.5) {"
                // Fade out: as progress goes 0->1, erase from top-left to bottom-right
                // Map progress so wipe line travels from before top-left to past bottom-right
                + "    float wipePos = wipeProgress * (1.0 + softness) - softness * 0.5;"
                // Alpha is 1 where diagonal > wipePos, 0 where diagonal < wipePos
                + "    alpha = smoothstep(wipePos - softness * 0.5, wipePos + softness * 0.5, diagonal);"
                + "  } else if (wipeDirection < -0.5) {"
                // Fade in: as progress goes 0->1, reveal from top-left to bottom-right
                + "    float wipePos = wipeProgress * (1.0 + softness) - softness * 0.5;"
                // Alpha is 0 where diagonal > wipePos, 1 where diagonal < wipePos
                + "    alpha = 1.0 - smoothstep(wipePos - softness * 0.5, wipePos + softness * 0.5, diagonal);"
                + "  }"
                + "  texColor.a *= alpha;"
                + "  gl_FragColor = texColor;"
                + "}";
    }
}

