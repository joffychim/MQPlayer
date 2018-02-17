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

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.drm.DecryptionException;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.MimeTypes;

import java.nio.ByteBuffer;
import java.util.List;

import static com.google.android.exoplayer2.ext.ffmpeg.FFmpegPacketBuffer.BUFFER_FLAG_DECODE_AGAIN;

/**
 * ffmpeg decoder.
 */
/* package */ final class FFmpegDecoder extends
        FFmpegBaseDecoder<FFmpegPacketBuffer, FFmpegFrameBuffer, FFmpegDecoderException> {

    static final int OUTPUT_MODE_NONE = -1;
    static final int OUTPUT_MODE_YUV = 0;
    static final int OUTPUT_MODE_RGB = 1;

    private static final int NO_ERROR = 0;
    private static final int DECODE_ERROR = 1;
    private static final int DRM_ERROR = 2;
    private static final int DECODE_AGAIN = 3;
    private static final int DECODE_EOF = 4;
    private static final int OUTPUT_BUFFER_ALLOCATE_FAILED = 5;

    private final ExoMediaCrypto exoMediaCrypto;
    private final long ffmpegDecContext;

    private volatile int outputMode;

    /**
     * Creates a ffmpeg decoder.
     *
     * @param numInputBuffers        The number of input buffers.
     * @param numOutputBuffers       The number of output buffers.
     * @param initialInputBufferSize The initial size of each input buffer.
     * @param exoMediaCrypto         The {@link ExoMediaCrypto} object required for decoding encrypted
     *                               content. Maybe null and can be ignored if decoder does not handle encrypted content.
     * @throws FFmpegDecoderException Thrown if an exception occurs when initializing the decoder.
     */
    public FFmpegDecoder(Format format, int numInputBuffers, int numOutputBuffers, int initialInputBufferSize,
                         ExoMediaCrypto exoMediaCrypto) throws FFmpegDecoderException {
        super(new FFmpegPacketBuffer[numInputBuffers], new FFmpegFrameBuffer[numOutputBuffers]);
        if (!FFmpegLibrary.isAvailable()) {
            throw new FFmpegDecoderException("Failed to load decoder native libraries.");
        }
        this.exoMediaCrypto = exoMediaCrypto;
        if (exoMediaCrypto != null && !FFmpegLibrary.ffmpegIsSecureDecodeSupported()) {
            throw new FFmpegDecoderException("FFmpeg decoder does not support secure decode.");
        }
        String mimeType = format.sampleMimeType;
        // TODO 目前仅仅支持H264
        ffmpegDecContext = ffmpegInit("h264", getExtraData(mimeType, format.initializationData), Util.getCpuNumCores() + 1);
        if (ffmpegDecContext == 0) {
            throw new FFmpegDecoderException("Failed to initialize decoder");
        }
        setInitialInputBufferSize(initialInputBufferSize);
    }

    @Override
    public String getName() {
        return "libffmpeg" + FFmpegLibrary.getVersion();
    }

    /**
     * Sets the output mode for frames rendered by the decoder.
     *
     * @param outputMode The output mode. One of {@link #OUTPUT_MODE_NONE}, {@link #OUTPUT_MODE_RGB}
     *                   and {@link #OUTPUT_MODE_YUV}.
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
    protected void resetDecoder() {
        ffmpegFlushBuffers(ffmpegDecContext);
    }

    @Override
    protected FFmpegDecoderException sendPacket(FFmpegPacketBuffer inputBuffer, boolean decodeOnly, boolean endOfStream) {
        ByteBuffer inputData = inputBuffer.data;
        int inputSize = inputData.limit();
        CryptoInfo cryptoInfo = inputBuffer.cryptoInfo;
        final long result = inputBuffer.isEncrypted()
                ? ffmpegSecureDecode(ffmpegDecContext,
                inputData,
                inputSize,
                exoMediaCrypto,
                cryptoInfo.mode,
                cryptoInfo.key,
                cryptoInfo.iv,
                cryptoInfo.numSubSamples,
                cryptoInfo.numBytesOfClearData,
                cryptoInfo.numBytesOfEncryptedData,
                inputBuffer.timeUs,
                decodeOnly,
                endOfStream)
                : ffmpegDecode(ffmpegDecContext,
                inputData,
                inputSize,
                inputBuffer.timeUs,
                decodeOnly,
                endOfStream);
        if (result != NO_ERROR) {
            if (result == DRM_ERROR) {
                String message = "Drm error: " + ffmpegGetErrorMessage(ffmpegDecContext);
                DecryptionException cause = new DecryptionException(
                        ffmpegGetErrorCode(ffmpegDecContext), message);
                return new FFmpegDecoderException(message, cause);
            } else if (result == DECODE_AGAIN) {
                inputBuffer.addFlag(BUFFER_FLAG_DECODE_AGAIN);
            } else {
                return new FFmpegDecoderException("Decode error: " + ffmpegGetErrorMessage(ffmpegDecContext));
            }
        }
        return null;
    }

    @Override
    protected FFmpegDecoderException getFrame(FFmpegFrameBuffer outputBuffer) {
        outputBuffer.init(outputMode);
        int getFrameResult = ffmpegGetFrame(ffmpegDecContext, outputBuffer);
        if (getFrameResult == DECODE_AGAIN) {
            outputBuffer.timeUs = -1L;
        } else if (getFrameResult == OUTPUT_BUFFER_ALLOCATE_FAILED) {
            return new FFmpegDecoderException("Buffer initialization failed.");
        } else if (getFrameResult != NO_ERROR && getFrameResult != DECODE_EOF) {
            return new FFmpegDecoderException("GetFrame error: " + ffmpegGetErrorMessage(ffmpegDecContext));
        }
        return null;
    }

    @Override
    protected void releaseOutputBuffer(FFmpegFrameBuffer buffer) {
        super.releaseOutputBuffer(buffer);
    }

    @Override
    public void release() {
        super.release();
        ffmpegClose(ffmpegDecContext);
    }

    private static byte[] getExtraData(String mimeType, List<byte[]> initializationData) {
        switch (mimeType) {
            case MimeTypes.VIDEO_MP4:
            case MimeTypes.VIDEO_H264:
                byte[] header0 = initializationData.get(0);
                byte[] header1 = initializationData.get(1);
                byte[] extraData = new byte[header0.length + header1.length + 6];
                extraData[0] = (byte) (header0.length >> 8);
                extraData[1] = (byte) (header0.length & 0xFF);
                System.arraycopy(header0, 0, extraData, 2, header0.length);
                extraData[header0.length + 2] = 0;
                extraData[header0.length + 3] = 0;
                extraData[header0.length + 4] = (byte) (header1.length >> 8);
                extraData[header0.length + 5] = (byte) (header1.length & 0xFF);
                System.arraycopy(header1, 0, extraData, header0.length + 6, header1.length);
                return extraData;
            default:
                // Other codecs do not require extra data.
                return null;
        }
    }

    private native long ffmpegInit(String codecName, byte[] extraData, int threadCount);

    private native int ffmpegClose(long context);

    private native void ffmpegFlushBuffers(long context);

    private native int ffmpegDecode(long context,
                                     ByteBuffer encoded,
                                     int length,
                                     long timeUs,
                                     boolean isDecodeOnly,
                                     boolean isEndOfStream);

    private native int ffmpegSecureDecode(long context,
                                           ByteBuffer encoded,
                                           int length,
                                           ExoMediaCrypto mediaCrypto,
                                           int inputMode,
                                           byte[] key,
                                           byte[] iv,
                                           int numSubSamples,
                                           int[] numBytesOfClearData,
                                           int[] numBytesOfEncryptedData,
                                           long timeUs,
                                           boolean isDecodeOnly,
                                           boolean isEndOfStream);

    private native int ffmpegGetFrame(long context, FFmpegFrameBuffer outputBuffer);

    private native int ffmpegGetErrorCode(long context);

    private native String ffmpegGetErrorMessage(long context);

}
