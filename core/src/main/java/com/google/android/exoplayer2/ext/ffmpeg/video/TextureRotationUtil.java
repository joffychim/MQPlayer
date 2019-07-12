/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.ffmpeg.video;

import java.nio.FloatBuffer;

public class TextureRotationUtil {
    /**
     * 原始顶点标号为
     * 1 2
     * 3 4
     */

    public static final float TEXTURE_NO_ROTATION[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    /**
     * 逆时针旋转顶点标号为
     * 2 4
     * 1 3
     */
    public static final float TEXTURE_ROTATED_90[] = {
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            0.0f, 0.0f,
    };
    public static final float TEXTURE_ROTATED_180[] = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
    };
    public static final float TEXTURE_ROTATED_270[] = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
    };

    public static final float CUBE[] = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
    };


    private TextureRotationUtil() {}

    public static float[] getRotation(final int rotation, final boolean flipHorizontal,
                                      final boolean flipVertical) {
        float[] rotatedTex;
        switch (rotation) {
            case 90:
                rotatedTex = TEXTURE_ROTATED_90;
                break;
            case 180:
                rotatedTex = TEXTURE_ROTATED_180;
                break;
            case 270:
                rotatedTex = TEXTURE_ROTATED_270;
                break;
            case 0:
            case 360:
            default:
                rotatedTex = TEXTURE_NO_ROTATION;
                break;
        }
        if (flipHorizontal) {
            rotatedTex = new float[]{
                    flip(rotatedTex[0]), rotatedTex[1],
                    flip(rotatedTex[2]), rotatedTex[3],
                    flip(rotatedTex[4]), rotatedTex[5],
                    flip(rotatedTex[6]), rotatedTex[7],
            };
        }
        if (flipVertical) {
            rotatedTex = new float[]{
                    rotatedTex[0], flip(rotatedTex[1]),
                    rotatedTex[2], flip(rotatedTex[3]),
                    rotatedTex[4], flip(rotatedTex[5]),
                    rotatedTex[6], flip(rotatedTex[7]),
            };
        }
        return rotatedTex;
    }

    public static void rotate(FloatBuffer normalTextureCoordinates, int rotation) {
        if (rotation == 0) return;

        rotation = 360 - rotation;

        float p0, p1, p2, p3, p4, p5, p6, p7;
        p0 = normalTextureCoordinates.get(0);
        p1 = normalTextureCoordinates.get(1);
        p2 = normalTextureCoordinates.get(2);
        p3 = normalTextureCoordinates.get(3);
        p4 = normalTextureCoordinates.get(4);
        p5 = normalTextureCoordinates.get(5);
        p6 = normalTextureCoordinates.get(6);
        p7 = normalTextureCoordinates.get(7);

        if (rotation == 90) {
            normalTextureCoordinates.put(0, p4);
            normalTextureCoordinates.put(1, p5);

            normalTextureCoordinates.put(2, p0);
            normalTextureCoordinates.put(3, p1);

            normalTextureCoordinates.put(4, p6);
            normalTextureCoordinates.put(5, p7);

            normalTextureCoordinates.put(6, p2);
            normalTextureCoordinates.put(7, p3);
        } else if (rotation == 180) {
            normalTextureCoordinates.put(0, p6);
            normalTextureCoordinates.put(1, p7);

            normalTextureCoordinates.put(2, p4);
            normalTextureCoordinates.put(3, p5);

            normalTextureCoordinates.put(4, p2);
            normalTextureCoordinates.put(5, p3);

            normalTextureCoordinates.put(6, p0);
            normalTextureCoordinates.put(7, p1);
        }  else if (rotation == 270) {
            normalTextureCoordinates.put(0, p2);
            normalTextureCoordinates.put(1, p3);

            normalTextureCoordinates.put(2, p6);
            normalTextureCoordinates.put(3, p7);

            normalTextureCoordinates.put(4, p0);
            normalTextureCoordinates.put(5, p1);

            normalTextureCoordinates.put(6, p4);
            normalTextureCoordinates.put(7, p5);
        }
    }

    private static float flip(final float i) {
        if (i == 0.0f) {
            return 1.0f;
        }
        return 0.0f;
    }
}
