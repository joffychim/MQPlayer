//
// Created by joffychim on 2019-07-04.
//

#ifndef MQPLAYER_FFMPEG_LIBRARY_H
#define MQPLAYER_FFMPEG_LIBRARY_H

#include <jni.h>

extern "C" {
#include <libavcodec/avcodec.h>
}

AVCodec *getCodecByName(JNIEnv* env, jstring codecName);

#endif //MQPLAYER_FFMPEG_LIBRARY_H
