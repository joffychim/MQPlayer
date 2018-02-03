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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.drm.DecryptionException;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import java.nio.ByteBuffer;

/**
 * ffmpeg decoder.
 */
/* package */ final class FFmpegDecoder extends
    SimpleDecoder<FFmpegPacketBuffer, FFmpegFrameBuffer, FFmpegDecoderException> {

  public static final int OUTPUT_MODE_NONE = -1;
  public static final int OUTPUT_MODE_YUV = 0;
  public static final int OUTPUT_MODE_RGB = 1;

  private static final int NO_ERROR = 0;
  private static final int DECODE_ERROR = 1;
  private static final int DRM_ERROR = 2;

  private final ExoMediaCrypto exoMediaCrypto;
  private final long vpxDecContext;

  private volatile int outputMode;

  /**
   * Creates a VP9 decoder.
   *
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer.
   * @param exoMediaCrypto The {@link ExoMediaCrypto} object required for decoding encrypted
   *     content. Maybe null and can be ignored if decoder does not handle encrypted content.
   * @throws FFmpegDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public FFmpegDecoder(int numInputBuffers, int numOutputBuffers, int initialInputBufferSize,
                       ExoMediaCrypto exoMediaCrypto) throws FFmpegDecoderException {
    super(new FFmpegPacketBuffer[numInputBuffers], new FFmpegFrameBuffer[numOutputBuffers]);
    if (!FFmpegLibrary.isAvailable()) {
      throw new FFmpegDecoderException("Failed to load decoder native libraries.");
    }
    this.exoMediaCrypto = exoMediaCrypto;
    if (exoMediaCrypto != null && !FFmpegLibrary.vpxIsSecureDecodeSupported()) {
      throw new FFmpegDecoderException("Vpx decoder does not support secure decode.");
    }
    vpxDecContext = vpxInit();
    if (vpxDecContext == 0) {
      throw new FFmpegDecoderException("Failed to initialize decoder");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  @Override
  public String getName() {
    return "libvpx" + FFmpegLibrary.getVersion();
  }

  /**
   * Sets the output mode for frames rendered by the decoder.
   *
   * @param outputMode The output mode. One of {@link #OUTPUT_MODE_NONE}, {@link #OUTPUT_MODE_RGB}
   *     and {@link #OUTPUT_MODE_YUV}.
   */
  public void setOutputMode(int outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  protected FFmpegPacketBuffer createInputBuffer() {
    return new FFmpegPacketBuffer();
  }

  @Override
  protected FFmpegFrameBuffer createOutputBuffer() {
    return new FFmpegFrameBuffer(this);
  }

  @Override
  protected void releaseOutputBuffer(FFmpegFrameBuffer buffer) {
    super.releaseOutputBuffer(buffer);
  }

  @Override
  protected FFmpegDecoderException decode(FFmpegPacketBuffer inputBuffer, FFmpegFrameBuffer outputBuffer,
                                          boolean reset) {
    ByteBuffer inputData = inputBuffer.data;
    int inputSize = inputData.limit();
    CryptoInfo cryptoInfo = inputBuffer.cryptoInfo;
    final long result = inputBuffer.isEncrypted()
        ? vpxSecureDecode(vpxDecContext, inputData, inputSize, exoMediaCrypto,
        cryptoInfo.mode, cryptoInfo.key, cryptoInfo.iv, cryptoInfo.numSubSamples,
        cryptoInfo.numBytesOfClearData, cryptoInfo.numBytesOfEncryptedData)
        : vpxDecode(vpxDecContext, inputData, inputSize);
    if (result != NO_ERROR) {
      if (result == DRM_ERROR) {
        String message = "Drm error: " + vpxGetErrorMessage(vpxDecContext);
        DecryptionException cause = new DecryptionException(
            vpxGetErrorCode(vpxDecContext), message);
        return new FFmpegDecoderException(message, cause);
      } else {
        return new FFmpegDecoderException("Decode error: " + vpxGetErrorMessage(vpxDecContext));
      }
    }

    if (!inputBuffer.isDecodeOnly()) {
      outputBuffer.init(inputBuffer.timeUs, outputMode);
      int getFrameResult = vpxGetFrame(vpxDecContext, outputBuffer);
      if (getFrameResult == 1) {
        outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
      } else if (getFrameResult == -1) {
        return new FFmpegDecoderException("Buffer initialization failed.");
      }
      outputBuffer.colorInfo = inputBuffer.colorInfo;
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    vpxClose(vpxDecContext);
  }

  private native long vpxInit();
  private native long vpxClose(long context);
  private native long vpxDecode(long context, ByteBuffer encoded, int length);
  private native long vpxSecureDecode(long context, ByteBuffer encoded, int length,
      ExoMediaCrypto mediaCrypto, int inputMode, byte[] key, byte[] iv,
      int numSubSamples, int[] numBytesOfClearData, int[] numBytesOfEncryptedData);
  private native int vpxGetFrame(long context, FFmpegFrameBuffer outputBuffer);
  private native int vpxGetErrorCode(long context);
  private native String vpxGetErrorMessage(long context);

}
