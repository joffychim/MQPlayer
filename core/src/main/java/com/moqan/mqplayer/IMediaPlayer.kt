package com.moqan.mqplayer

import android.content.Context
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView

/*
 * @author joffychim  <zhanzenghui@bytedance.com>
 * @since 2019/07/02
 */
class UnknownMediaPlayerException(val what: Int, val extra: Int) : Exception()

interface IMediaPlayer {
    interface EventListener {
        fun onPrepared()
        fun onStart()
        fun onPlayComplete()
        fun onPlaying()
        
        fun onSeekComplete(positionAfterSeek: Long)

        fun onPause()
        fun onStop()
        fun onReset()
        fun onRelease()
        
        fun onPositionUpdate(position: Long, duration: Long)
        fun onVolumeChanged(newV1: Float, newV2: Float)
        fun onBuffering(loadedPercentage: Int)
        fun onError(e: Exception)
        fun onVideoSizeChanged(width: Int, height: Int)
    }

    fun setDataSource(uri: Uri)
    fun prepare()

    fun start()
    fun resume()
    fun pause()
    fun stop()
    fun reset()
    fun release()

    fun seekTo(position: Long)

    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun isPlaying(): Boolean
    fun isPrepared(): Boolean
    fun isReleased(): Boolean

    fun setSurfaceView(surfaceView: SurfaceView?)
    fun setSurfaceHolder(surfaceHolder: SurfaceHolder?)
    fun setTextureView(textureView: TextureView?)

    fun setSpeed(speed: Float)
    fun setVolume(audioVolume: Float)
    fun setAudioStreamType(streamType: Int)

    fun addListener(listener: EventListener)
    fun removeListener(listener: EventListener)
}