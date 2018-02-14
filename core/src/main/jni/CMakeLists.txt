cmake_minimum_required(VERSION 3.4.1)

# 指定include文件
include_directories(BEFORE ${PROJECT_SOURCE_DIR}/../../../libs/ffmpeg/include)
include_directories(BEFORE ${PROJECT_SOURCE_DIR}/../../../libs/yuv/include)

add_library(mqplayer
            SHARED
            ffmpeg_decoder.cpp
            ffmpeg_library.cpp)

find_library(log-lib
             log)
# 找ffmpeg
find_library(ffmpeg-lib
             ffmpeg
             ${PROJECT_SOURCE_DIR}/../../../libs/ffmpeg/armeabi-v7a
             NO_CMAKE_FIND_ROOT_PATH)

find_library(yuv-lib
             yuv
             ${PROJECT_SOURCE_DIR}/../../../libs/yuv/armeabi-v7a
             NO_CMAKE_FIND_ROOT_PATH)

target_link_libraries(mqplayer
                       ${log-lib}
                       ${ffmpeg-lib}
                       ${yuv-lib})