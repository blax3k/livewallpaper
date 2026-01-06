package com.example.livewallpaper;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
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
    private int scrollOffsetHandle;
    private int parallaxMultiplierHandle;

    private List<Sprite> sprites;

    private float[] projectionMatrix = new float[16];
    private float currentScrollOffset = 0f;
    private int gyroOffsetXHandle;
    private int gyroOffsetYHandle;
    private float currentGyroOffsetX = 0f;
    private float currentGyroOffsetY = 0f;

    // Cumulative angle tracking for gyroscope (maintains position after tilt)
    private float cumulativeAngleX = 0f;  // Pitch - tilt forward/backward
    private float cumulativeAngleY = 0f;  // Roll - tilt left/right
    private long lastGyroUpdateTime = 0;

    // Low-pass filter for smoothing
    private float[] prevSensorData = {0f, 0f, 0f};
    private float motionOffsetStrength = 1.0f;
    private float motionOffsetLimit = 1.0f;

    // Sensitivity of gyro-to-offset conversion (tunable). Higher = larger movement per radian.
    private float gyroSensitivity = 0.8f;

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
        scrollOffsetHandle = GLES20.glGetUniformLocation(program, "scrollOffset");
        parallaxMultiplierHandle = GLES20.glGetAttribLocation(program, "parallaxMultiplier");
        gyroOffsetXHandle = GLES20.glGetUniformLocation(program, "gyroOffsetX");
        gyroOffsetYHandle = GLES20.glGetUniformLocation(program, "gyroOffsetY");

        addSprites();

        Log.d(TAG, "Surface created");
    }

    private void addSprites()
    {
        // Create sprites with position and size
        Sprite landscapeSprite = new Sprite(context, R.drawable.landscape, 1.5f, 1.5f);
        landscapeSprite.setParallaxMultiplier(0.5f);  // Background moves slower
        sprites.add(landscapeSprite);

        Sprite knightSprite = new Sprite(context, R.drawable.knight, 1f, 1.5f);
        knightSprite.setParallaxMultiplier(1.0f);  // Foreground moves with scroll
        sprites.add(knightSprite);

    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / height;

        // Set up orthographic projection that accounts for aspect ratio
        // Keep vertical range at -1 to 1, scale horizontal by aspect ratio
        if (aspectRatio > 1) {
            // Landscape: wider than tall

            Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, 1f, -1f, -1f, 1f);
        } else {
            // Portrait: taller than wide
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, 1f / aspectRatio, -1f / aspectRatio, -1f, 1f);
        }

        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);

        // Set projection matrix
        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);

        // Set scroll offset uniform (applied by all sprites with their own multiplier)
        GLES20.glUniform1f(scrollOffsetHandle, currentScrollOffset);

        // Set gyroscope offsets for device tilt movement
        GLES20.glUniform1f(gyroOffsetXHandle, currentGyroOffsetX);
        GLES20.glUniform1f(gyroOffsetYHandle, currentGyroOffsetY);

        // Draw all sprites
        for (Sprite sprite : sprites) {
            sprite.draw(positionHandle, texCoordHandle, samplerHandle, parallaxMultiplierHandle);
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

    @Override
    public void onScrollOffsetChanged(float offsetX) {
        // Treat offset=0.5 as the centered position. We want positive offset to move sprites left when user scrolls right,
        // so compute (0.5 - offsetX). At offsetX == 0.5 this becomes 0 (no displacement).
        this.currentScrollOffset = 0.5f - offsetX;
    }

    @Override
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        try {
            // Get current time to calculate delta time
            long currentTime = System.nanoTime();
            float deltaTime = lastGyroUpdateTime == 0 ? 0 : (currentTime - lastGyroUpdateTime) / 1_000_000_000.0f;
            lastGyroUpdateTime = currentTime;

            // Clamp deltaTime to prevent huge jumps if app was paused
            if (deltaTime > 0.1f) {
                deltaTime = 0.1f;
            }

            // Get raw sensor data
            float[] rawSensorData = new float[3];
            rawSensorData[0] = rotationX;
            rawSensorData[1] = rotationY;
            rawSensorData[2] = rotationZ;

            // Apply low-pass filter to smooth sensor data
            float[] lowPassSensorData = lowPass(rawSensorData, prevSensorData);
            prevSensorData = rawSensorData;

            // Integrate rotation rates to get cumulative angles (this maintains position after tilt)
            cumulativeAngleX += lowPassSensorData[0] * deltaTime;
            cumulativeAngleY += lowPassSensorData[1] * deltaTime;

            // Guard rails: clamp cumulative angles so offsets cannot grow beyond motionOffsetLimit
            float angleLimit = motionOffsetLimit / Math.max(gyroSensitivity, 1e-6f);
            if (cumulativeAngleX > angleLimit) cumulativeAngleX = angleLimit;
            else if (cumulativeAngleX < -angleLimit) cumulativeAngleX = -angleLimit;
            if (cumulativeAngleY > angleLimit) cumulativeAngleY = angleLimit;
            else if (cumulativeAngleY < -angleLimit) cumulativeAngleY = -angleLimit;

            // Convert cumulative angles to screen offsets using configurable sensitivity
            float targetOffsetX = cumulativeAngleY * gyroSensitivity;
            float targetOffsetY = cumulativeAngleX * gyroSensitivity;

            // Clamp to motion limits
            if (targetOffsetX > motionOffsetLimit) {
                targetOffsetX = motionOffsetLimit;
            } else if (targetOffsetX < -motionOffsetLimit) {
                targetOffsetX = -motionOffsetLimit;
            }

            if (targetOffsetY > motionOffsetLimit) {
                targetOffsetY = motionOffsetLimit;
            } else if (targetOffsetY < -motionOffsetLimit) {
                targetOffsetY = -motionOffsetLimit;
            }

            // Smooth the transition to target offset
            float smoothingFactor = 0.2f;
            currentGyroOffsetX = currentGyroOffsetX * (1.0f - smoothingFactor) + targetOffsetX * smoothingFactor;
            currentGyroOffsetY = currentGyroOffsetY * (1.0f - smoothingFactor) + targetOffsetY * smoothingFactor;
        } catch (Exception e) {
            Log.e(TAG, "Error in onGyroscopeChanged: " + e.getMessage(), e);
        }
    }

    private int compileShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    /**
     * Low-pass filter for gyroscope sensor data to reduce noise/jitter.
     * Blends current sensor values with previous values.
     *
     * @param current current sensor data
     * @param previous previous sensor data
     * @return filtered sensor data
     */
    private float[] lowPass(float[] current, float[] previous) {
        float alpha = 0.25f;  // Low-pass filter coefficient (0.0-1.0, lower = more smoothing)
        float[] output = new float[3];
        for (int i = 0; i < 3; i++) {
            output[i] = alpha * current[i] + (1.0f - alpha) * previous[i];
        }
        return output;
    }

    private String getVertexShaderCode() {
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
        Matrix.orthoM(projectionMatrix, 0, left, right, bottom, top, near, far);
    }
}
