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

import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.util.LibraryLoader;

/**
 * Configures and queries the underlying native library.
 */
public final class FFmpegLibrary {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.ffmpeg");
  }

  private static final LibraryLoader LOADER = new LibraryLoader("ffmpeg", "ffmpegJNI");

  private FFmpegLibrary() {}

  /**
   * Override the names of the FFmpeg native libraries. If an application wishes to call this method,
   * it must do so before calling any other method defined by this class, and before instantiating a
   * {@link FFmpegVideoRenderer} instance.
   *
   * @param libraries The names of the FFmpeg native libraries.
   */
  public static void setLibraries(String... libraries) {
    LOADER.setLibraries(libraries);
  }

  /**
   * Returns whether the underlying library is available, loading it if necessary.
   */
  public static boolean isAvailable() {
    return LOADER.isAvailable();
  }

  /**
   * Returns the version of the underlying library if available, or null otherwise.
   */
  public static String getVersion() {
    return isAvailable() ? ffmpegGetVersion() : null;
  }

  /**
   * Returns the configuration string with which the underlying library was built if available, or
   * null otherwise.
   */
  public static String getBuildConfig() {
    return isAvailable() ? ffmpegGetBuildConfig() : null;
  }

  /**
   * Returns true if the underlying libffmpeg library supports high bit depth.
   */
  public static boolean isHighBitDepthSupported() {
    String config = getBuildConfig();
    int indexHbd = config != null
        ? config.indexOf("--enable-vp9-highbitdepth") : -1;
    return indexHbd >= 0;
  }

  private static native String ffmpegGetVersion();
  private static native String ffmpegGetBuildConfig();
  public static native boolean ffmpegIsSecureDecodeSupported();

}