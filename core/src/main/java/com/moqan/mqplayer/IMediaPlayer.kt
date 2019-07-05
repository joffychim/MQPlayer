package com.moqan.mqplayer

import android.media.session.PlaybackState
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView

/*
 * @author joffychim  <zhanzenghui@bytedance.com>
 * @since 2019/07/02
 */
interface IMediaPlayer {
    interface EventListener {
        fun onPlaybackStateChanged(playingWhenReady: Boolean, playbackState: Int) {}

        fun onSeekComplete(positionAfterSeek: Long) {}
        fun onError(e: Exception) {}

        fun onVolumeChanged(volume: Float) {}

        fun onDurationChanged(duration: Long) {}
        fun onRenderedFirstFrame() {}
        fun onPlaySpeedChange(speed: Float) {}
        fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {}
    }
    fun setDisplay(surfaceView: SurfaceView?)
    fun setDisplay(surfaceHolder: SurfaceHolder?)
    fun setDisplay(textureView: TextureView?)

    fun setDataSource(uri: Uri)

    fun setPlayWhenReady(play: Boolean)

    fun prepare()
    fun stop()
    fun reset()

    fun release()

    fun isPlaying(): Boolean

    fun seekTo(position: Long)

    fun getCurrentPosition(): Long
    fun getDuration(): Long

    fun setSpeed(speed: Float)
    fun setVolume(audioVolume: Float)
    fun setAudioStreamType(streamType: Int)

    fun addListener(listener: EventListener)
    fun removeListener(listener: EventListener)
}