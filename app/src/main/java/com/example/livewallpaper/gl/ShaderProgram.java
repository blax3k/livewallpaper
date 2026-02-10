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
                + "attribute vec2 vNormalizedPosition;"
                + "attribute float parallaxMultiplier;"
                + "varying vec2 texCoord;"
                + "varying vec2 normalizedPosition;"

                + "void main() {"
                + "  vec4 position = vPosition;"
                + "  position.x += scrollOffset * parallaxMultiplier + gyroOffsetX * parallaxMultiplier;"
                + "  position.y += gyroOffsetY * parallaxMultiplier;"
                + "  gl_Position = projectionMatrix * position;"
                + "  texCoord = vTexCoord;"
                + "  normalizedPosition = vNormalizedPosition;"
                + "}";
    }

    public static String getFragmentShaderCode() {
        // wipeProgress: 0.0 to 1.0 transition progress
        // wipeDirection: 1.0 = fade out (wipe erases from top-left to bottom-right)
        //               -1.0 = fade in (wipe reveals from top-left to bottom-right)
        //                0.0 = no wipe effect (fully visible)
        // useColorOverride: 1.0 = use overrideColor instead of texture
        // WIPE_FEATHER: controls the softness of the wipe edge (0.0 sharp, 0.4 moderate, 0.6+ very soft)
        // normalizedPosition: sprite's vertex position normalized to 0-1 range (independent of texture sheet)
        return "precision mediump float;"
                + "uniform sampler2D samplerTexture;"
                + "uniform float wipeProgress;"
                + "uniform float wipeDirection;"
                + "uniform vec4 overrideColor;"
                + "uniform float useColorOverride;"
                + "varying vec2 texCoord;"
                + "varying vec2 normalizedPosition;"
                + "const float WIPE_FEATHER = 0.1;"
                + "void main() {"
                + "  vec4 texColor;"
                + "  if (useColorOverride > 0.5) {"
                + "    texColor = overrideColor;"
                + "  } else {"
                + "    texColor = texture2D(samplerTexture, texCoord);"
                + "  }"
                + "  float diagonal = (normalizedPosition.x + (1.0 - normalizedPosition.y)) / 2.0;"
                + "  float wipeFade = 1.0;"
                + "  if (wipeDirection > 0.5) {"
                + "    float wipePos = wipeProgress * (1.0 + WIPE_FEATHER) - WIPE_FEATHER * 0.5;"
                + "    wipeFade = smoothstep(wipePos - WIPE_FEATHER * 0.5, wipePos + WIPE_FEATHER * 0.5, diagonal);"
                + "  } else if (wipeDirection < -0.5) {"
                + "    float wipePos = wipeProgress * (1.0 + WIPE_FEATHER) - WIPE_FEATHER * 0.5;"
                + "    wipeFade = 1.0 - smoothstep(wipePos - WIPE_FEATHER * 0.5, wipePos + WIPE_FEATHER * 0.5, diagonal);"
                + "  }"
                + "  if (wipeFade < 0.5) {"
                + "    float edgeBlend = wipeFade * 2.0;"
                + "    texColor.a = texColor.a * edgeBlend;"
                + "  }"
                + "  gl_FragColor = texColor;"
                + "}";
    }
}

