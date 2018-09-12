package com.moqan.mqplayer.egl;
/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLHelper {
    private static final String TAG = "GLHelper";
    /** Identity matrix for general use.  Don't modify or life will get weird. */
    public static final int NO_TEXTURE = -1;
    public static final int SIZE_OF_FLOAT = 4;

    private static ThreadLocal<float[]> sTempMatrix = new ThreadLocal<float[]>() {
        @Override
        protected float[] initialValue() {
            return new float[32];
        }
    };
    private static final int RESULT_MATRIX_OFFSET = 16;

    private GLHelper() { // do not instantiate
    }

    public static long getContextHandle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return EGL14.eglGetCurrentContext().getNativeHandle();
        } else {
            return 0;
        }
    }

    public static int createProgram(Context applicationContext, @RawRes int vertexSourceRawId,
            @RawRes int fragmentSourceRawId) {
        String vertexSource = readTextFromRawResource(applicationContext, vertexSourceRawId);
        String fragmentSource = readTextFromRawResource(applicationContext, fragmentSourceRawId);
        return createProgram(vertexSource, fragmentSource);
    }

    public static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }

    public static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    /**
     * @param textureTarget Texture类型。
     * 1. 相机用 GLES11Ext.GL_TEXTURE_EXTERNAL_OES
     * 2. 图片用GLES20.GL_TEXTURE_2D
     * @param minFilter 缩小过滤类型 (1.GL_NEAREST ; 2.GL_LINEAR)
     * @param magFilter 放大过滤类型
     * @param wrapS X方向边缘环绕
     * @param wrapT Y方向边缘环绕
     * @return 返回创建的 Texture ID
     */
    public static int createTexture(int textureTarget, @Nullable Bitmap bitmap, int minFilter,
            int magFilter, int wrapS, int wrapT) {
        int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);
        GLHelper.checkGlError("glGenTextures");
        bindTexture(textureTarget, textureHandle[0]);
        GLHelper.checkGlError("glBindTexture " + textureHandle[0]);
        GLES20.glTexParameterf(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, minFilter);
        GLES20.glTexParameterf(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, magFilter); //线性插值
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, wrapS);
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, wrapT);

        if (bitmap != null) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        }

        GLHelper.checkGlError("glTexParameter");
        return textureHandle[0];
    }

    public static int createTexture(int textureTarget) {
        return createTexture(textureTarget, null, GLES20.GL_LINEAR, GLES20.GL_LINEAR,
                GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_CLAMP_TO_EDGE);
    }

    public static int createTexture(int textureTarget, Bitmap bitmap) {
        return createTexture(textureTarget, bitmap, GLES20.GL_LINEAR, GLES20.GL_LINEAR,
                GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_CLAMP_TO_EDGE);
    }

    public static void checkGlError() {
        checkGlError("");
    }

    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != 0) {
            Throwable t = new Throwable();
            Log.e(TAG, "GL error: " + error + ", op:" + op, t);
        }
    }

    private static String readTextFromRawResource(final Context applicationContext,
            @RawRes final int resourceId) {
        final InputStream inputStream =
                applicationContext.getResources().openRawResource(resourceId);
        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String nextLine;
        final StringBuilder body = new StringBuilder();
        try {
            while ((nextLine = bufferedReader.readLine()) != null) {
                body.append(nextLine);
                body.append('\n');
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                bufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return body.toString();
    }

    public static int createTextureWithTextContent(String text) {
        // Create an empty, mutable bitmap
        Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        // get a canvas to paint over the bitmap
        Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(0, 0, 255, 0);
        // get a background image from resources
        // note the image format must match the bitmap format
        //        Drawable background = context.getResources().getDrawable(R.drawable.background);
        //        background.setBounds(0, 0, 256, 256);
        //        background.draw(canvas); // draw the background to our bitmap
        // Draw the text
        Paint textPaint = new Paint();
        textPaint.setTextSize(32);
        textPaint.setAntiAlias(true);
        textPaint.setARGB(0xff, 0xff, 0xff, 0xff);
        // draw the text centered
        canvas.drawText(text, 16, 112, textPaint);

        int[] textures = new int[1];

        //Generate one texture pointer...
        GLES20.glGenTextures(1, textures, 0);

        //...and bind it to our array
        bindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

        //Create Nearest Filtered Texture
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);

        //Different possible texture parameters, e.g. GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        //Alpha blending
        //GLES20.glEnable(GLES20.GL_BLEND);
        //GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        //Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        //Clean up
        bitmap.recycle();

        return textures[0];
    }

    public static void bindTexture(int target, int texture) {
        GLES20.glBindTexture(target, texture);
    }

    public static FloatBuffer createBuffer(float[] values) {
        // First create an nio buffer, then create a VBO from it.
        int size = values.length * SIZE_OF_FLOAT;
        FloatBuffer buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values, 0, values.length).position(0);
        return buffer;
    }

    public static void glGenBuffers(int n, int[] buffers, int offset) {
        GLES20.glGenBuffers(n, buffers, offset);
        checkGlError("glGenBuffers");
    }

    public static void deleteBuffer(int bufferId) {
        GLES20.glDeleteBuffers(1, new int[] { bufferId }, 0);
        checkGlError("deleteBuffer");
    }

    public static void deleteBuffers(int n, int[] buffers, int offset) {
        GLES20.glDeleteBuffers(n, buffers, offset);
        checkGlError("deleteBuffers");
    }

    public static int uploadBuffer(FloatBuffer buf) {
        return uploadBuffer(buf, SIZE_OF_FLOAT);
    }

    public static int uploadBuffer(ByteBuffer buf) {
        return uploadBuffer(buf, 1);
    }

    public static int uploadBuffer(Buffer buffer, int elementSize) {
        int tempIntArray[] = new int[1];
        glGenBuffers(1, tempIntArray, 0);
        int bufferId = tempIntArray[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferId);
        checkGlError("glBindBuffer");
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, buffer.capacity() * elementSize, buffer,
                GLES20.GL_STATIC_DRAW);
        checkGlError("glBufferData");
        return bufferId;
    }

    public static void genProjectionMatrix(float[] matrix,
                                           int offset,
                                           float[] preMatrix,
                                           int preMatrixOffset,
                                           int originalWidth, int originalHeight,
                                           int x, int y,
                                           int width, int height) {
        Matrix.setIdentityM(matrix, offset);
        Matrix.orthoM(matrix, offset, 0, originalWidth, 0, originalHeight, -1, 1);

        float[] tempMatrix = sTempMatrix.get();
        Matrix.setIdentityM(tempMatrix, 0);

        if (preMatrix == null) {
            Matrix.translateM(tempMatrix, 0, x, y, 0);
        } else {
            Matrix.translateM(tempMatrix, 0, preMatrix, preMatrixOffset, x, y, 0);
        }
        Matrix.scaleM(tempMatrix, 0, width, height, 1);

        Matrix.multiplyMM(tempMatrix, RESULT_MATRIX_OFFSET, matrix, 0, tempMatrix, 0);
        System.arraycopy(tempMatrix, RESULT_MATRIX_OFFSET, matrix, offset, 16);
    }
}