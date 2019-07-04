//
// Created by joffy on 18/2/7.
//

#include "ffmpeg_api_define.h"

extern "C" {
    #include <libavcodec/avcodec.h>
}

LIBRARY_FUNC(jstring, ffmpegIsSecureDecodeSupported) {
    return 0;
}

LIBRARY_FUNC(jstring, ffmpegGetVersion) {
    char ver[25];
    sprintf(ver, "%d", avcodec_version());
    return env->NewStringUTF(ver);
}

LIBRARY_FUNC(jstring, ffmpegGetBuildConfig) {
    return env->NewStringUTF("moqan ffmpeg");
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
