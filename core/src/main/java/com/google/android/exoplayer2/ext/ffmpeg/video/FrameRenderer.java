/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.ffmpeg.video;

import android.graphics.Color;
import android.opengl.GLES20;

import com.moqan.mqplayer.egl.GLViewRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GLSurfaceView.Renderer implementation that can render YUV Frames returned by libffmpeg after
 * decoding. It does the YUV to RGB color conversion in the Fragment Shader.
 */
class FrameRenderer implements GLViewRenderer, IFrameRenderer {
    private static final float[] kColorConversion601 = {
            1.164f, 1.164f, 1.164f,
            0.0f, -0.392f, 2.017f,
            1.596f, -0.813f, 0.0f,
    };

    private static final float[] kColorConversion709 = {
            1.164f, 1.164f, 1.164f,
            0.0f, -0.213f, 2.112f,
            1.793f, -0.533f, 0.0f,
    };

    private static final float[] kColorConversion2020 = {
            1.168f, 1.168f, 1.168f,
            0.0f, -0.188f, 2.148f,
            1.683f, -0.652f, 0.0f,
    };

    private static final String VERTEX_SHADER =
            "varying vec2 interp_tc;\n"
                    + "attribute vec4 in_pos;\n"
                    + "attribute vec2 in_tc;\n"
                    + "void main() {\n"
                    + "  gl_Position = in_pos;\n"
                    + "  interp_tc = in_tc;\n"
                    + "}\n";

    private static final String[] TEXTURE_UNIFORMS = {"y_tex", "u_tex", "v_tex"};
    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "varying vec2 interp_tc;\n"
                    + "uniform sampler2D y_tex;\n"
                    + "uniform sampler2D u_tex;\n"
                    + "uniform sampler2D v_tex;\n"
                    + "uniform float bitDepth;\n"
                    + "uniform mat3 mColorConversion;\n"
                    + "void main() {\n"
                    + "vec3 yuv;\n"
                    + "if(interp_tc.x < 0.0 || interp_tc.x > 1.0 || interp_tc.y < 0.0 || interp_tc.y > 1.0){"
                    + "gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);\n"
                    + "} else {"
                    + "if(bitDepth==2.0){\n"
                    + "vec3 yuv_l;\n"
                    + "vec3 yuv_h;\n"
                    + "yuv_l.x = texture2D(y_tex, interp_tc).r;\n"
                    + "yuv_h.x = texture2D(y_tex, interp_tc).a;\n"
                    + "yuv_l.y = texture2D(u_tex, interp_tc).r;\n"
                    + "yuv_h.y = texture2D(u_tex, interp_tc).a;\n"
                    + "yuv_l.z = texture2D(v_tex, interp_tc).r;\n"
                    + "yuv_h.z = texture2D(v_tex, interp_tc).a;\n"
                    + "yuv = (yuv_l * 255.0 + yuv_h * 255.0 * 256.0) / (1023.0) - vec3(16.0 / 255.0, 0.5, 0.5);\n"
                    + "}else{\n"
                    + "yuv.x = texture2D(y_tex, interp_tc).r - 0.0625;\n"
                    + "yuv.y = texture2D(u_tex, interp_tc).r - 0.5;\n"
                    + "yuv.z = texture2D(v_tex, interp_tc).r - 0.5;\n"
                    + "}\n"
                    + "gl_FragColor = vec4(mColorConversion * yuv, 1.0);\n"
                    + "}"
                    + "}\n";

    private static final FloatBuffer TEXTURE_VERTICES = nativeFloatBuffer(
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, 1.0f,
            1.0f, -1.0f);
    private final int[] yuvTextures = new int[3];
    private final AtomicReference<FrameBuffer> pendingOutputBufferReference;

    // Kept in a field rather than a local variable so that it doesn't get garbage collected before
    // glDrawArrays uses it.
    @SuppressWarnings("FieldCanBeLocal")
    private FloatBuffer textureCoords;
    private int program;
    private int texLocation;
    private int colorMatrixLocation;
    private int bitDepthLocation;
    private int previousWidth;
    private int previousStride;

    private int surfaceWidth, surfaceHeight;
    private int previousSurfaceWidth, previousSurfaceHeight;
    private int previousRotationDegree;

    private float bgColorAlpha = 1.0f;
    private float bgColorRed = 0.f;
    private float bgColorGreen = 0.f;
    private float bgColorBlue = 0.f;

    private FrameScaleType scaleType = FrameScaleType.FIT_CENTER;
    private FrameScaleType previousScaleType = scaleType;

    private FrameBuffer renderedOutputBuffer; // Accessed only from the GL thread.

    public FrameRenderer() {
        previousWidth = -1;
        previousStride = -1;
        pendingOutputBufferReference = new AtomicReference<>();
    }

    public void setBackgroundColor(int bgColor) {
        this.bgColorAlpha = Color.alpha(bgColor) / 255.0f;
        this.bgColorRed = Color.red(bgColor) / 255.0f;
        this.bgColorGreen = Color.green(bgColor) / 255.0f;
        this.bgColorBlue = Color.blue(bgColor) / 255.0f;
    }

    public void setScaleType(FrameScaleType scaleType) {
        this.scaleType = scaleType;
    }

    @Override
    public void onSurfaceCreated() {
        // Create the GL program.
        program = GLES20.glCreateProgram();

        // Add the vertex and fragment shaders.
        addShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER, program);
        addShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER, program);

        // Link the GL program.
        GLES20.glLinkProgram(program);
        int[] result = new int[]{
                GLES20.GL_FALSE
        };
        result[0] = GLES20.GL_FALSE;
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
        abortUnless(result[0] == GLES20.GL_TRUE, GLES20.glGetProgramInfoLog(program));
        GLES20.glUseProgram(program);
        int posLocation = GLES20.glGetAttribLocation(program, "in_pos");
        GLES20.glEnableVertexAttribArray(posLocation);
        GLES20.glVertexAttribPointer(
                posLocation, 2, GLES20.GL_FLOAT, false, 0, TEXTURE_VERTICES);
        texLocation = GLES20.glGetAttribLocation(program, "in_tc");
        GLES20.glEnableVertexAttribArray(texLocation);
        checkNoGLES2Error();
        GLES20.glEnable(GLES20.GL_BLEND);
        checkNoGLES2Error();
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        checkNoGLES2Error();
        bitDepthLocation = GLES20.glGetUniformLocation(program, "bitDepth");
        checkNoGLES2Error();
        colorMatrixLocation = GLES20.glGetUniformLocation(program, "mColorConversion");
        checkNoGLES2Error();
        setupTextures();
        checkNoGLES2Error();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        surfaceWidth = width;
        surfaceHeight = height;
    }

    @Override
    public void onDrawFrame() {
        FrameBuffer pendingOutputBuffer = pendingOutputBufferReference.getAndSet(null);
        if (pendingOutputBuffer == null && renderedOutputBuffer == null) {
            // There is no output buffer to render at the moment.
            return;
        }
        if (pendingOutputBuffer != null) {
            if (renderedOutputBuffer != null) {
                renderedOutputBuffer.release();
            }
            renderedOutputBuffer = pendingOutputBuffer;
        }

        FrameBuffer outputBuffer = renderedOutputBuffer;
        float[] colorConversion = kColorConversion709;
        int bitDepth = outputBuffer.bitDepth;
        int format = bitDepth == 1 ? GLES20.GL_LUMINANCE : GLES20.GL_LUMINANCE_ALPHA;

        GLES20.glUniformMatrix3fv(colorMatrixLocation, 1, false, colorConversion, 0);
        GLES20.glUniform1f(bitDepthLocation, bitDepth);

        for (int i = 0; i < 3; i++) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);

            int width = outputBuffer.yuvStrides[i] / bitDepth;
            int height = (i == 0) ? outputBuffer.height : outputBuffer.height / 2;

            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format,
                    width, height, 0, format, GLES20.GL_UNSIGNED_BYTE,
                    outputBuffer.yuvPlanes[i]);
        }

        // Set cropping of stride if either width,stride,surface width or surface height has changed.
        if (previousWidth != outputBuffer.width ||
                previousStride != outputBuffer.yuvStrides[0] ||
                previousSurfaceWidth != surfaceWidth ||
                previousSurfaceHeight != surfaceHeight ||
                previousScaleType != scaleType ||
                previousRotationDegree != outputBuffer.rotationDegree) {

            int width = outputBuffer.width;
            int height = outputBuffer.height;
            int rotationDegree = outputBuffer.rotationDegree;
            if (rotationDegree == 90 || rotationDegree == 270) {
                int tmp = width;
                width = height;
                height = tmp;
            }

            float verticalAspect = 0.f;
            float horizontalAspect = 0.f;
            if (scaleType != FrameScaleType.FIT_XY) {
                boolean fitX = false;
                boolean fitY = false;
                if (scaleType == FrameScaleType.FIT_CENTER) {
                    if (width / (float)surfaceWidth >= height / (float)surfaceHeight) {
                        fitX = true;
                    } else {
                        fitY = true;
                    }
                }
                if (fitX || scaleType == FrameScaleType.FIT_X) {
                    float textureHeight = (float) surfaceWidth * height / width;
                    verticalAspect = (textureHeight - surfaceHeight) / 2f / textureHeight;
                } else if (fitY || scaleType == FrameScaleType.FIT_Y) {
                    float textureWidth = (float) surfaceHeight * width / height;
                    horizontalAspect = (textureWidth - surfaceWidth) / 2f / textureWidth;
                }
            }

            if (rotationDegree == 90 || rotationDegree == 270) {
                float tmp = horizontalAspect;
                horizontalAspect = verticalAspect;
                verticalAspect = tmp;
            }

            float crop = (float) outputBuffer.width * bitDepth / outputBuffer.yuvStrides[0];
            textureCoords = nativeFloatBuffer(
                    crop * horizontalAspect, verticalAspect,
                    crop * horizontalAspect, 1 - verticalAspect,
                    crop * (1 - horizontalAspect), verticalAspect,
                    crop * (1 - horizontalAspect), 1 - verticalAspect);
            TextureRotationUtil.rotate(textureCoords, outputBuffer.rotationDegree);

            GLES20.glVertexAttribPointer(
                    texLocation, 2, GLES20.GL_FLOAT, false, 0, textureCoords);

            previousWidth = outputBuffer.width;
            previousStride = outputBuffer.yuvStrides[0];
            previousSurfaceWidth = surfaceWidth;
            previousSurfaceHeight = surfaceHeight;
            previousScaleType = scaleType;
            previousRotationDegree = rotationDegree;
        }
        GLES20.glClearColor(bgColorRed, bgColorGreen, bgColorBlue, bgColorAlpha);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkNoGLES2Error();
    }

    private void addShader(int type, String source, int program) {
        int[] result = new int[]{
                GLES20.GL_FALSE
        };
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
        abortUnless(result[0] == GLES20.GL_TRUE,
                GLES20.glGetShaderInfoLog(shader) + ", source: " + source);
        GLES20.glAttachShader(program, shader);
        GLES20.glDeleteShader(shader);

        checkNoGLES2Error();
    }

    private void setupTextures() {
        GLES20.glGenTextures(3, yuvTextures, 0);
        for (int i = 0; i < 3; i++) {
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, TEXTURE_UNIFORMS[i]), i);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }
        checkNoGLES2Error();
    }

    private void abortUnless(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }

    private void checkNoGLES2Error() {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new RuntimeException("GLES20 error: " + error);
        }
    }

    private static FloatBuffer nativeFloatBuffer(float... array) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(array.length * 4).order(
                ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(array);
        buffer.flip();
        return buffer;
    }

    /**
     * Set a frame to be rendered. This should be followed by a call to
     * FFmpegVideoSurfaceView.requestRender() to actually render the frame.
     *
     * @param outputBuffer OutputBuffer containing the YUV Frame to be rendered
     */
    public void setOutputBuffer(FrameBuffer outputBuffer) {
        FrameBuffer oldPendingOutputBuffer = pendingOutputBufferReference.getAndSet(outputBuffer);
        if (oldPendingOutputBuffer != null) {
            // The old pending output buffer will never be used for rendering, so release it now.
            oldPendingOutputBuffer.release();
        }
    }
}
