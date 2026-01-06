package com.example.livewallpaper;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple example renderer that displays a blue square with a texture (knight.png) in the center of the view.
 */
public class SimpleRenderer implements GLWallpaperRenderer {
    private static final String TAG = "SimpleRenderer";

    private Context context;
    private int program;
    private int positionHandle;
    private int texCoordHandle;
    private int samplerHandle;
    private int projectionMatrixHandle;

    private List<Sprite> sprites;

    private float[] projectionMatrix = new float[16];

    public SimpleRenderer(Context context) {
        this.context = context;
        this.sprites = new ArrayList<>();
    }

    @Override
    public void onSurfaceCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Compile shaders and create program
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, getVertexShaderCode());
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, getFragmentShaderCode());

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord");
        samplerHandle = GLES20.glGetUniformLocation(program, "samplerTexture");
        projectionMatrixHandle = GLES20.glGetUniformLocation(program, "projectionMatrix");

        // Create sprites with position and size
        Sprite landscapeSprite = new Sprite(context, R.drawable.landscape, 1.5f, 1.5f);
        sprites.add(landscapeSprite);

        Sprite knightSprite = new Sprite(context, R.drawable.knight, 1f, 1.5f);
        sprites.add(knightSprite);


        Log.d(TAG, "Surface created");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / height;

        // Set up orthographic projection that accounts for aspect ratio
        // Keep vertical range at -1 to 1, scale horizontal by aspect ratio
        if (aspectRatio > 1) {
            // Landscape: wider than tall
            setOrthographicProjection(-aspectRatio, aspectRatio, 1f, -1f, -1f, 1f);
        } else {
            // Portrait: taller than wide
            setOrthographicProjection(-1f, 1f, 1f / aspectRatio, -1f / aspectRatio, -1f, 1f);
        }

        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);

        // Set projection matrix
        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);

        // Draw all sprites
        for (Sprite sprite : sprites) {
            sprite.draw(positionHandle, texCoordHandle, samplerHandle);
        }
    }

    @Override
    public void onDestroy() {
        // Destroy all sprites
        for (Sprite sprite : sprites) {
            sprite.destroy();
        }
        sprites.clear();

        if (program != 0) {
            GLES20.glDeleteProgram(program);
        }
        Log.d(TAG, "Renderer destroyed");
    }

    private int compileShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private String getVertexShaderCode() {
        return "uniform mat4 projectionMatrix;"
                + "attribute vec4 vPosition;"
                + "attribute vec2 vTexCoord;"
                + "varying vec2 texCoord;"
                + "void main() {"
                + "  gl_Position = projectionMatrix * vPosition;"
                + "  texCoord = vTexCoord;"
                + "}";
    }

    private String getFragmentShaderCode() {
        return "precision mediump float;"
                + "uniform sampler2D samplerTexture;"
                + "varying vec2 texCoord;"
                + "void main() {"
                + "  vec4 texColor = texture2D(samplerTexture, texCoord);"
                + "  gl_FragColor = texColor;"
                + "}";
    }

    private void setOrthographicProjection(float left, float right, float bottom, float top, float near, float far) {
        float[] orthoMatrix = new float[16];

        // Initialize identity matrix
        for (int i = 0; i < 16; i++) {
            orthoMatrix[i] = 0f;
        }

        // Set orthographic projection matrix values
        orthoMatrix[0] = 2f / (right - left);
        orthoMatrix[5] = 2f / (top - bottom);
        orthoMatrix[10] = -2f / (far - near);
        orthoMatrix[12] = -(right + left) / (right - left);
        orthoMatrix[13] = -(top + bottom) / (top - bottom);
        orthoMatrix[14] = -(far + near) / (far - near);
        orthoMatrix[15] = 1f;

        projectionMatrix = orthoMatrix;
    }
}

