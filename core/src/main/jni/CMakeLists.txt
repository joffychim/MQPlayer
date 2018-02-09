cmake_minimum_required(VERSION 3.4.1)

# 指定include文件
include_directories(BEFORE ${PROJECT_SOURCE_DIR}/../../../libs/ffmpeg/include)

# 找ffmpeg
add_library(ffmpeg SHARED IMPORTED)
set(ffmpegLibPath ${PROJECT_SOURCE_DIR}/../../../libs/ffmpeg/armeabi-v7a/libffmpeg.so)
set_target_properties(ffmpeg PROPERTIES IMPORTED_LOCATION ${ffmpegLibPath})

add_library(mqplayer
            SHARED
            ffmpeg_library.cpp)

find_library(log-lib
              log)

target_link_libraries(mqplayer
                       ${log-lib}
                       ffmpeg)