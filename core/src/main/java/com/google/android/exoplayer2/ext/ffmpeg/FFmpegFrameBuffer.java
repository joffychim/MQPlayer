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
package com.google.android.exoplayer2.ext.ffmpeg;

import com.google.android.exoplayer2.decoder.OutputBuffer;
import com.google.android.exoplayer2.video.ColorInfo;

import java.nio.ByteBuffer;

/**
 * Output buffer containing video frame data, populated by {@link FFmpegDecoder}.
 */
/* package */ final class FFmpegFrameBuffer extends OutputBuffer {
  private final FFmpegDecoder owner;

  /**
   * RGB buffer for RGB mode.
   */
  public ByteBuffer data;
  public int width;
  public int height;
  public ColorInfo colorInfo;

  public FFmpegFrameBuffer(FFmpegDecoder owner) {
    this.owner = owner;
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }

  /**
   * Resizes the buffer based on the given dimensions. Called via JNI after decoding completes.
   * @return Whether the buffer was resized successfully.
   */
  public boolean initForRgbFrame(int width, int height) {
    this.width = width;
    this.height = height;
    if (!isSafeToMultiply(width, height) || !isSafeToMultiply(width * height, 2)) {
      return false;
    }
    int minimumRgbSize = width * height * 2;
    initData(minimumRgbSize);
    return true;
  }

  private void initData(int size) {
    if (data == null || data.capacity() < size) {
      data = ByteBuffer.allocateDirect(size);
    } else {
      data.position(0);
      data.limit(size);
    }
  }

  /**
   * Ensures that the result of multiplying individual numbers can fit into the size limit of an
   * integer.
   */
  private boolean isSafeToMultiply(int a, int b) {
    return a >= 0 && b >= 0 && !(b > 0 && a >= Integer.MAX_VALUE / b);
  }

  public boolean hasFlag(int flag) {
    return getFlag(flag);
  }
}
