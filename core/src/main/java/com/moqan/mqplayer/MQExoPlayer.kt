package com.moqan.mqplayer

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.CallSuper
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto
import com.google.android.exoplayer2.ext.Constant
import com.google.android.exoplayer2.ext.Constant.*
import com.google.android.exoplayer2.ext.ffmpeg.video.FrameScaleType
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import java.util.*

/**
 * @since 18/9/14
 * @author joffychim
 */
internal class MQExoPlayer @JvmOverloads constructor(
        context: Context,
        rendererFactory: RenderersFactory?,
        trackSelector: TrackSelector?,
        loadControl: LoadControl?,
        drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?,
        analyticsCollectorFactory: AnalyticsCollector.Factory = AnalyticsCollector.Factory()
) : SimpleExoPlayer(context, rendererFactory, trackSelector, loadControl, drmSessionManager,
        DefaultBandwidthMeter(), analyticsCollectorFactory, Looper.getMainLooper()) {

    private var origSurfaceTextureListener: TextureView.SurfaceTextureListener? = null
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            origSurfaceTextureListener?.onSurfaceTextureSizeChanged(surface, width, height)
            onSurfaceSizeChanged(width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            origSurfaceTextureListener?.onSurfaceTextureUpdated(surface)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return origSurfaceTextureListener?.onSurfaceTextureDestroyed(surface) ?: true
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            origSurfaceTextureListener?.onSurfaceTextureAvailable(surface, width, height)
            onSurfaceSizeChanged(width, height)
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            onSurfaceSizeChanged(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {
        }

    }

    private var textureView: TextureView? = null
    private var surfaceHolder: SurfaceHolder? = null

    override fun release() {
        clearListener()

        onPlayReleased()
        super.release()
    }

    private fun onSurfaceSizeChanged(width: Int, height: Int) {
        val messages = mutableListOf<PlayerMessage>()
        val size = Point(width, height);

        renderers.firstOrNull { it.trackType == C.TRACK_TYPE_VIDEO }?.let {
            val message = createMessage(it).setType(Constant.MSG_SURFACE_SIZE_CHANGED).setPayload(size).send()
            messages.add(message)
        }

        messages.forEach { it.blockUntilDelivered() }
    }

    @CallSuper
    protected fun onPlayReleased() {
        val messages = mutableListOf<PlayerMessage>()
        renderers.forEach {
            messages.add(createMessage(it).setType(MSG_PLAY_RELEASED).send())
        }

        messages.forEach { it.blockUntilDelivered() }
    }

    override fun setVideoSurface(surface: Surface?) {
        throw IllegalAccessError("please call setVideoTextureView or setVideoSurfaceView or setVideoSurfaceHolder")
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        super.setVideoSurfaceHolder(surfaceHolder)

        clearListener()
        this.surfaceHolder = surfaceHolder
        this.textureView = null
        surfaceHolder?.addCallback(surfaceCallback)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        super.setVideoSurfaceView(surfaceView)

        clearListener()
        this.surfaceHolder = surfaceView?.holder
        this.textureView = null
        surfaceHolder?.addCallback(surfaceCallback)
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        super.setVideoTextureView(textureView)

        clearListener()
        this.textureView = textureView
        this.surfaceHolder = null
        // fix bug
        if (textureView?.isAvailable == true) {
            onSurfaceSizeChanged(textureView.width, textureView.height)
        }

        origSurfaceTextureListener = textureView?.surfaceTextureListener
        textureView?.surfaceTextureListener = surfaceTextureListener
    }

    private fun clearListener() {
        if (this.textureView?.surfaceTextureListener == surfaceTextureListener) {
            this.textureView?.surfaceTextureListener = origSurfaceTextureListener
        }
        origSurfaceTextureListener = null

        this.surfaceHolder?.removeCallback(surfaceCallback)
    }

    fun setBackgroundColor(color: Int) {
        val messages = mutableListOf<PlayerMessage>()
        renderers.firstOrNull { it.trackType == C.TRACK_TYPE_VIDEO }?.let {
            messages.add(createMessage(it).setType(MSG_SET_BACKGROUND_COLOR).setPayload(color).send())
        }

        messages.forEach { it.blockUntilDelivered() }
    }

    fun setScaleType(scaleType: FrameScaleType) {
        val messages = mutableListOf<PlayerMessage>()
        renderers.firstOrNull { it.trackType == C.TRACK_TYPE_VIDEO }?.let {
            messages.add(createMessage(it).setType(MSG_SET_SCALE_TYPE).setPayload(scaleType).send())
        }

        messages.forEach { it.blockUntilDelivered() }
    }
}