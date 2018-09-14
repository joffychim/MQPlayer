//
// Created by joffy on 18/2/7.
//

#include "ffmpeg_api_define.h"

extern "C" {
    #include "libyuv.h"
    #include <libavcodec/avcodec.h>
}

#define UNSUPPORTED_ERROR -2
#define OTHER_ERROR -1
#define NO_ERROR 0
#define DECODE_ERROR 1
#define DECODE_AGAIN 3
#define DECODE_EOF 4
#define OUTPUT_BUFFER_ALLOCATE_FAILED 5

#define ERROR_STRING_BUFFER_LENGTH 256

// JNI references for FFmpegFrameBuffer class.
static jmethodID initForRgbFrame;
static jmethodID initForYuvFrame;
static jfieldID dataField;
static jfieldID timeFrameUsField;

static int lastFFmpegErrorCode = 0;
static AVFrame* holdFrame = NULL;

// 打印错误
void logError(const char *functionName, int errorNumber);
// 获取相应的编码器
AVCodec *getCodecByName(JNIEnv* env, jstring codecName);
// 初始化java层对应的成员变量或者方法
void initJavaRef(JNIEnv *env);
// 创建上下文
AVCodecContext *createContext(JNIEnv *env, AVCodec *codec,
                              jint  width, jint height,
                              jbyteArray extraData, jint threadCount);
// 释放上下文
void releaseContext(AVCodecContext *context);
// 解码相应packet
int decodePacket(AVCodecContext *context, AVPacket *packet);
// 把解码后的frame放入到outputBuffer
int putFrameToOutputBuffer(JNIEnv *env, AVFrame *frame, jobject jOutputBuffer);

DECODER_FUNC(jlong , ffmpegInit, jstring codecName, jint  width,
             jint height, jbyteArray extraData, jint threadCount) {
    avcodec_register_all();
    AVCodec *codec = getCodecByName(env, codecName);
    if (!codec) {
        LOGE("Codec not found.");
        return 0;
    }

    initJavaRef(env);
    return (jlong) createContext(env, codec, width, height, extraData, threadCount);
}

DECODER_FUNC(jint , ffmpegClose, jlong jContext) {
    AVCodecContext* pCodecContext = (AVCodecContext*)jContext;
    releaseContext(pCodecContext);
    if (holdFrame != NULL) {
        av_frame_free(&holdFrame);
    }
    return NO_ERROR;
}

DECODER_FUNC(void , ffmpegFlushBuffers, jlong jContext) {
    AVCodecContext* context = (AVCodecContext*)jContext;
    avcodec_flush_buffers(context);
}

DECODER_FUNC(jint , ffmpegDecode, jlong jContext, jobject encoded, jint len,
             jlong timeUs,
             jboolean isDecodeOnly,
             jboolean isEndOfStream,
             jboolean isKeyFrame) {
    AVCodecContext* context = (AVCodecContext*)jContext;
    uint8_t *packetBuffer = (uint8_t *) env->GetDirectBufferAddress(encoded);

    AVPacket packet;
    av_init_packet(&packet);
    packet.data = packetBuffer;
    packet.size = len;

    packet.pts = timeUs;
    if (isDecodeOnly) {
        packet.flags |= AV_PKT_FLAG_DISCARD;
    }

    if (isKeyFrame) {
        packet.flags |= AV_PKT_FLAG_KEY;
    }

    int result = decodePacket(context, &packet);
    if (result == NO_ERROR && isEndOfStream) {
        result = decodePacket(context, NULL);
        if (result == DECODE_AGAIN) {
            result = NO_ERROR;
        }
    }

    return result;
}

DECODER_FUNC(jint , ffmpegSecureDecode,
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
             jboolean isEndOfStream,
             jboolean isKeyFrame) {
    return UNSUPPORTED_ERROR;
}

DECODER_FUNC(jint, ffmpegGetFrame, jlong jContext, jobject jOutputBuffer) {
    int result = 0;
    AVCodecContext* context = (AVCodecContext*)jContext;

    if (holdFrame == NULL) {
        holdFrame = av_frame_alloc();
    }

    int error = avcodec_receive_frame(context, holdFrame);
    // 测试只有三帧的视频，send null packet后，解最后一帧出现AVERROR_INVALIDDATA错误
    // 所以把AVERROR_INVALIDDATA当做EOF处理
    // TODO 把AVERROR_INVALIDDATA当做EOF处理是否得当？
    if (error == 0) {
        result = putFrameToOutputBuffer(env, holdFrame, jOutputBuffer);
    } else if (error == AVERROR(EAGAIN)){
        // packet还不够
        result = DECODE_AGAIN;
    } else if (error == AVERROR_EOF || error == AVERROR_INVALIDDATA) {
        result = DECODE_EOF;
    } else {
        result = DECODE_ERROR;
    }
    lastFFmpegErrorCode = error;
    return result;
}

DECODER_FUNC(jint , ffmpegGetErrorCode, jlong jContext) {
    return lastFFmpegErrorCode;
}

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
                              jint  width, jint height,
                              jbyteArray extraData, jint threadCount) {
    AVCodecContext *context = avcodec_alloc_context3(codec);
    if (!context) {
        LOGE("Failed to allocate avcodec context.");
        return NULL;
    }
    if (extraData != NULL) {
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
    //av_dict_set_int(&opts, "lowres", true, 0);

    int result = avcodec_open2(context, codec, &opts);
    if (result < 0) {
        logError("avcodec_open2", result);
        releaseContext(context);
        return NULL;
    }

    context->width = width;
    context->height = height;

    return context;
}

void initJavaRef(JNIEnv *env) {
    // Populate JNI References.
    const jclass outputBufferClass = env->FindClass(
            "com/google/android/exoplayer2/ext/ffmpeg/FFmpegFrameBuffer");
    initForYuvFrame = env->GetMethodID(outputBufferClass, "initForYuvFrame",
                                       "(IIIII)Z");
    dataField = env->GetFieldID(outputBufferClass, "data",
                                "Ljava/nio/ByteBuffer;");
    timeFrameUsField = env->GetFieldID(outputBufferClass, "timeUs", "J");
}

void releaseContext(AVCodecContext *pCodecContext) {
    if (!pCodecContext) {
        return;
    }
    if (pCodecContext->extradata != NULL) {
        av_free(pCodecContext->extradata);
        pCodecContext->extradata = NULL;
    }
    avcodec_free_context(&pCodecContext);
}

int decodePacket(AVCodecContext *context, AVPacket *packet) {
    // Queue input data.
    int result = NO_ERROR;
    int ffError = avcodec_send_packet(context, packet);
    if (ffError == AVERROR(EAGAIN)) {
        result = DECODE_AGAIN;
    } else if (ffError != 0 && ffError != AVERROR_INVALIDDATA) {
        result = DECODE_ERROR;
    }

    lastFFmpegErrorCode = ffError;
    return result;
}

int putFrameToOutputBuffer(JNIEnv *env, AVFrame *frame, jobject jOutputBuffer) {
    const int kOutputModeYuv = 0;
    const int kOutputModeRgb = 1;

    env->SetLongField(jOutputBuffer, timeFrameUsField, frame->pts);

    // resize buffer if required.
    jboolean initResult = env->CallBooleanMethod(
            jOutputBuffer, initForYuvFrame, frame->width, frame->height,
            frame->linesize[0], frame->linesize[1], 1);
    if (env->ExceptionCheck() || !initResult) {
        return OUTPUT_BUFFER_ALLOCATE_FAILED;
    }

    // get pointer to the data buffer.
    const jobject dataObject = env->GetObjectField(jOutputBuffer, dataField);
    jbyte* const data = reinterpret_cast<jbyte*>(env->GetDirectBufferAddress(dataObject));

    const int32_t uvHeight = (frame->height + 1) / 2;
    const uint64_t yLength = frame->linesize[0] * frame->height;
    const uint64_t uvLength = frame->linesize[1] * uvHeight;

    memcpy(data, frame->data[0], yLength);
    memcpy(data + yLength, frame->data[1], uvLength);
    memcpy(data + yLength + uvLength, frame->data[2], uvLength);

    return NO_ERROR;
}