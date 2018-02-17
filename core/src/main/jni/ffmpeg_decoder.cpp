//
// Created by joffy on 18/2/7.
//

#include "ffmpeg_api_define.h"

extern "C" {
    #include "libyuv.h"
    #include <libavcodec/avcodec.h>
}


// JNI references for FFmpegFrameBuffer class.
static jmethodID initForRgbFrame;
static jmethodID initForYuvFrame;
static jfieldID dataField;
static jfieldID outputModeField;
static jfieldID timeFrameUsField;

void releaseContext(AVCodecContext *context);
int decodePacket(AVCodecContext *context, AVPacket *packet,
                 uint8_t *outputBuffer, int outputSize);

#define ERROR_STRING_BUFFER_LENGTH 256
void logError(const char *functionName, int errorNumber) {
    char *buffer = (char *) malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
    av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
    LOGE("Error in %s: %s", functionName, buffer);
    free(buffer);
}

AVCodec *getCodecByName(JNIEnv* env, jstring codecName) {
    if (!codecName) {
        return NULL;
    }
    const char *codecNameChars = env->GetStringUTFChars(codecName, NULL);
    AVCodec *codec = avcodec_find_decoder_by_name(codecNameChars);
    env->ReleaseStringUTFChars(codecName, codecNameChars);
    return codec;
}

AVCodecContext *createContext(JNIEnv *env, AVCodec *codec,
                              jbyteArray extraData, jint threadCount) {
    AVCodecContext *context = avcodec_alloc_context3(codec);
    if (!context) {
        LOGE("Failed to allocate avcodec context.");
        return NULL;
    }
    if (extraData) {
        jsize size = env->GetArrayLength(extraData);
        context->extradata_size = size;
        context->extradata =
                (uint8_t *) av_malloc((size_t) (size + AV_INPUT_BUFFER_PADDING_SIZE));
        if (!context->extradata) {
            LOGE("Failed to allocate extradata.");
            releaseContext(context);
            return NULL;
        }
        env->GetByteArrayRegion(extraData, 0, size, (jbyte *) context->extradata);
    }
    AVDictionary *opts = NULL;
    av_dict_set_int(&opts, "threads", threadCount, 0);
    av_dict_set_int(&opts, "lowres", true, 0);

    int result = avcodec_open2(context, codec, &opts);
    if (result < 0) {
        logError("avcodec_open2", result);
        releaseContext(context);
        return NULL;
    }
    return context;
}

void initJavaRef(JNIEnv *env) {
    // Populate JNI References.
    const jclass outputBufferClass = env->FindClass(
            "com/google/android/exoplayer2/ext/ffmpeg/FFmpegFrameBuffer");
    initForYuvFrame = env->GetMethodID(outputBufferClass, "initForYuvFrame",
                                       "(IIIII)Z");
    initForRgbFrame = env->GetMethodID(outputBufferClass, "initForRgbFrame",
                                       "(II)Z");
    dataField = env->GetFieldID(outputBufferClass, "data",
                                "Ljava/nio/ByteBuffer;");
    outputModeField = env->GetFieldID(outputBufferClass, "mode", "I");

    timeFrameUsField = env->GetFieldID(outputBufferClass, "timeUs", "J");
}

void releaseContext(AVCodecContext *context) {
    if (!context) {
        return;
    }
    avcodec_free_context(&context);
}

int decodePacket(AVCodecContext *context, AVPacket *packet) {
    int result = 0;
    // Queue input data.
    result = avcodec_send_packet(context, packet);
    if (result == AVERROR(EAGAIN)) {
        return 3;
    } else if (result != 0) {
        return -1;
    }
    return 0;
}

int putFrame2OutputBuffer(JNIEnv *env, AVFrame* frame, jobject jOutputBuffer) {
    jboolean initResult = env->CallBooleanMethod(
            jOutputBuffer, initForRgbFrame, frame->width, frame->height);
    if (initResult == JNI_FALSE) {
        return -1;
    }

    // get pointer to the data buffer.
    const jobject dataObject = env->GetObjectField(jOutputBuffer, dataField);
    jbyte* const data =
            reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(dataObject));

    int width = frame->width;
    int height = frame->height;

    env->SetLongField(jOutputBuffer, timeFrameUsField, frame->pts);

    libyuv::I420ToRGB565((const uint8 *) frame->data[0],
                       frame->linesize[0],
                       (const uint8 *) frame->data[1],
                       frame->linesize[1],
                       (const uint8 *) frame->data[2],
                       frame->linesize[2],
                       (uint8 *) data, 2 * width, width, height);
    return 0;
}

DECODER_FUNC(jlong , ffmpegInit, jstring codecName, jbyteArray extraData, jint threadCount) {
    avcodec_register_all();
    AVCodec *codec = getCodecByName(env, codecName);
    if (!codec) {
        LOGE("Codec not found.");
        return 0L;
    }

    initJavaRef(env);
    return (jlong) createContext(env, codec, extraData, threadCount);
}

DECODER_FUNC(jlong , ffmpegClose, jlong jContext) {
    releaseContext((AVCodecContext*)jContext);
    return 0;
}

DECODER_FUNC(void , ffmpegFlushBuffers, jlong jContext) {
    AVCodecContext* context = (AVCodecContext*)jContext;
    avcodec_flush_buffers(context);
}

DECODER_FUNC(jlong, ffmpegDecode, jlong jContext, jobject encoded, jint len,
             jlong timeUs,
             jboolean isDecodeOnly,
             jboolean isEndOfStream) {
    AVCodecContext* context = (AVCodecContext*)jContext;
    uint8_t *packetBuffer = (uint8_t *) env->GetDirectBufferAddress(encoded);

    AVPacket packet;
    av_init_packet(&packet);
    packet.data = packetBuffer;
    packet.size = len;

    packet.pts = timeUs;
    if (isDecodeOnly) {
        packet.flags &= AV_PKT_FLAG_DISCARD;
    }

    int result = decodePacket(context, &packet);
    if (result == 0 && isEndOfStream) {
        result = decodePacket(context, NULL);
        if (result == 3) {
            result = 0;
        }
    }
    return result;
}

DECODER_FUNC(jlong, ffmpegSecureDecode,
             jlong jContext,
             jobject encoded,
             jint len,
             jobject mediaCrypto,
             jint inputMode,
             jbyteArray&,
             jbyteArray&,
             jint inputNumSubSamples,
             jintArray numBytesOfClearData,
             jintArray numBytesOfEncryptedData,
             jlong timeUs,
             jboolean isDecodeOnly,
             jboolean isEndOfStream) {
    return -2;
}

DECODER_FUNC(jint, ffmpegGetFrame, jlong jContext, jobject jOutputBuffer) {
    int result = 0;
    AVCodecContext* context = (AVCodecContext*)jContext;

    AVFrame* holdFrame = av_frame_alloc();
    int error = avcodec_receive_frame(context, holdFrame);
    if (error == 0) {
        putFrame2OutputBuffer(env, holdFrame, jOutputBuffer);
    } else if (error == AVERROR(EAGAIN)){
        // packet还不够
        result = 3;
    } else if (error == AVERROR_EOF) {
        result = 4;
    } else {
        result = 1;
    }
    av_frame_free(&holdFrame);
    return result;
}

DECODER_FUNC(jint , ffmpegGetErrorCode, jlong jContext) {
    return 0;
}

DECODER_FUNC(jstring , ffmpegGetErrorMessage, jlong jContext) {
    return env->NewStringUTF("");
}