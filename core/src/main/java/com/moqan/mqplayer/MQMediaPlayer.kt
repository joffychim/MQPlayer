package com.moqan.mqplayer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto
import com.google.android.exoplayer2.ext.ffmpeg.audio.SoftAudioRenderer
import com.google.android.exoplayer2.ext.ffmpeg.video.SoftVideoRenderer
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.*
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoRendererEventListener
import java.io.File
import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList


/*
 * @author joffychim  <zhanzenghui@bytedance.com>
 * @since 2019/07/02
 */
private class RenderFactoryImp : DefaultRenderersFactory {
    constructor(context: Context) : super(context)
    constructor(context: Context, drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?) : super(context, drmSessionManager)
    constructor(context: Context, extensionRendererMode: Int) : super(context, extensionRendererMode)
    constructor(context: Context, drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?, extensionRendererMode: Int) : super(context, drmSessionManager, extensionRendererMode)
    constructor(context: Context, extensionRendererMode: Int, allowedVideoJoiningTimeMs: Long) : super(context, extensionRendererMode, allowedVideoJoiningTimeMs)
    constructor(context: Context, drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?, extensionRendererMode: Int, allowedVideoJoiningTimeMs: Long) : super(context, drmSessionManager, extensionRendererMode, allowedVideoJoiningTimeMs)

    override fun buildAudioRenderers(context: Context?, extensionRendererMode: Int, mediaCodecSelector: MediaCodecSelector?, drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?, playClearSamplesWithoutKeys: Boolean, enableDecoderFallback: Boolean, audioProcessors: Array<out AudioProcessor>?, eventHandler: Handler?, eventListener: AudioRendererEventListener?, out: ArrayList<Renderer>) {
        val softRender = SoftAudioRenderer()
        out.add(softRender)
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, audioProcessors, eventHandler, eventListener, out)
    }

    override fun buildVideoRenderers(context: Context?, extensionRendererMode: Int, mediaCodecSelector: MediaCodecSelector?, drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?, playClearSamplesWithoutKeys: Boolean, enableDecoderFallback: Boolean, eventHandler: Handler?, eventListener: VideoRendererEventListener?, allowedVideoJoiningTimeMs: Long, out: ArrayList<Renderer>) {
        val softRenderer = SoftVideoRenderer(true,
                allowedVideoJoiningTimeMs, eventHandler, eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
                drmSessionManager, false)
        out.add(softRenderer)
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out)
    }
}

class MQMediaPlayer(private val context: Context) : IMediaPlayer {
    private val player = MQExoPlayer(context, RenderFactoryImp(context), DefaultTrackSelector(), DefaultLoadControl(), null)

    private val downloadDirectory = context.getExternalFilesDir(null) ?: context.filesDir
    private val downloadCache = SimpleCache(File(downloadDirectory, "downloads"), NoOpCacheEvictor(), ExoDatabaseProvider(context))
    private val dataSourceFactory: DataSource.Factory by lazy {
        val httpDataSource = DefaultHttpDataSourceFactory(Util.getUserAgent(context, "MQPlayer"))
        val upstreamFactory = DefaultDataSourceFactory(context, httpDataSource)
        CacheDataSourceFactory(downloadCache, upstreamFactory, FileDataSourceFactory(), null, CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null)
    }
    private var dataSource: MediaSource? = null

    private val listeners = CopyOnWriteArrayList<IMediaPlayer.EventListener>()

    private var lastPlayState = false
    private var prepared = false

    init {
        player.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (lastPlayState != playWhenReady) {
                    lastPlayState = playWhenReady;
                    if (playWhenReady) {
                    } else {

                    }
                }

                when (playbackState) {
                    Player.STATE_IDLE -> {

                    }

                    Player.STATE_BUFFERING -> {
                        listeners.forEach { it.onBuffering(player.bufferedPercentage) }
                    }

                    Player.STATE_READY -> {
                        listeners.forEach { it.onPlaying() }
                    }

                    Player.STATE_ENDED -> {
                        listeners.forEach { it.onPlayComplete() }
                    }
                }
            }

            override fun onSeekProcessed() {
                val position = player.currentPosition
                listeners.forEach {
                    it.onSeekComplete(position)
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {

            }

            override fun onPlayerError(error: ExoPlaybackException) {
                listeners.forEach {
                    it.onError(error)
                }
            }
        })
    }

    override fun setDataSource(uri: Uri) {
        @C.ContentType val type = Util.inferContentType(uri, null)
        val dataSource = when (type) {
            C.TYPE_DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            C.TYPE_SS -> SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            C.TYPE_HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            C.TYPE_OTHER -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
            else -> throw IllegalStateException("Unsupported type: $type")
        }
        this.dataSource = dataSource
    }

    override fun prepare() {
        player.prepare(dataSource, true, false)
        prepared = true
    }

    override fun start() {
        player.playWhenReady = true
    }

    override fun resume() {
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun stop() {
        player.playWhenReady = false
        player.stop()
    }

    override fun reset() {
        player.stop(true)
        this.dataSource = null
        this.prepared = false
    }

    override fun release() {
        player.release()
    }

    override fun seekTo(position: Long) {
        player.seekTo(position)
    }

    override fun getCurrentPosition() =  player.currentPosition

    override fun getDuration() = player.duration

    override fun isPlaying() = player.playWhenReady && player.playbackState == Player.STATE_READY

    override fun isPrepared() = prepared

    override fun isReleased(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setSurfaceView(surfaceView: SurfaceView?) {
        player.setVideoSurfaceView(surfaceView)
    }

    override fun setSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        player.setVideoSurfaceHolder(surfaceHolder)
    }

    override fun setTextureView(textureView: TextureView?) {
        player.setVideoTextureView(textureView)
    }

    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    override fun setVolume(audioVolume: Float) {
        player.volume = audioVolume
    }

    override fun setAudioStreamType(streamType: Int) {
        @C.AudioUsage val usage = Util.getAudioUsageForStreamType(streamType)
        @C.AudioContentType val contentType = Util.getAudioContentTypeForStreamType(streamType)
        player.audioAttributes = AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build()
    }

    override fun addListener(listener: IMediaPlayer.EventListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: IMediaPlayer.EventListener) {
        listeners.remove(listener)
    }

}