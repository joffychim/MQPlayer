package com.google.android.exoplayer2.ext

import android.graphics.Point
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto
import com.google.android.exoplayer2.ext.Constant.MSG_PLAY_RELEASED
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.util.Clock
import java.util.*

/**
 * @since 18/9/14
 * @author joffychim
 */
class MQExoPlayer @JvmOverloads constructor(
        renderersFactory: RenderersFactory?,
        trackSelector: TrackSelector?,
        loadControl: LoadControl?,
        drmSessionManager: DrmSessionManager<FrameworkMediaCrypto>?,
        analyticsCollectorFactory: AnalyticsCollector.Factory = AnalyticsCollector.Factory(),
        clock: Clock = Clock.DEFAULT)
    : SimpleExoPlayer(renderersFactory,
        trackSelector,
        loadControl,
        drmSessionManager,
        analyticsCollectorFactory,
        clock) {
    private val that = this

    private inner class InnerSurfaceTextureListener(val origSurfaceTextureListener: TextureView.SurfaceTextureListener?) : TextureView.SurfaceTextureListener {
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
        }
    }

    private val innerSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            onSurfaceSizeChanged(width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {
        }

    }

    private inner class InnerVideoComponent() : Player.VideoComponent by this {
        private var textureView: TextureView? = null
        private var surfaceHolder: SurfaceHolder? = null

        override fun setVideoTextureView(textureView: TextureView?) {
            clearListener()
            this.textureView = textureView
            that.setVideoTextureView(textureView)
            if (textureView?.isAvailable == true) {
                onSurfaceSizeChanged(textureView.width, textureView.height)
            }
            textureView?.surfaceTextureListener = InnerSurfaceTextureListener(textureView?.surfaceTextureListener)
        }

        override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
            clearListener()
            this.surfaceHolder = surfaceView?.holder
            that.setVideoSurfaceHolder(surfaceHolder)
            surfaceHolder?.addCallback(innerSurfaceCallback)
        }

        override fun setVideoSurface(surface: Surface?) {
            throw IllegalAccessError("please call setVideoTextureView or setVideoSurfaceView")
        }

        fun clearListener() {
            this.textureView?.surfaceTextureListener?.also {
                if (it is InnerSurfaceTextureListener) {
                    this.textureView?.surfaceTextureListener = it.origSurfaceTextureListener
                }
            }
            this.surfaceHolder?.removeCallback(innerSurfaceCallback)
        }
    }
    private val innerVideoComponent = InnerVideoComponent()

    override fun getVideoComponent(): Player.VideoComponent? {
        return innerVideoComponent
    }

    override fun release() {
        innerVideoComponent.clearListener()
        onPlayReleased()
        super.release()
    }

    private fun onSurfaceSizeChanged(width: Int, height: Int) {
        val messages = ArrayList<PlayerMessage>()
        for (renderer in renderers) {
            if (renderer.trackType == C.TRACK_TYPE_VIDEO) {
                val size = Point(width, height);
                messages.add(createMessage(renderer).setType(Constant.MSG_SURFACE_SIZE_CHANGED).setPayload(size).send())
            }
        }

        try {
            for (message in messages) {
                message.blockUntilDelivered()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun onPlayReleased() {
        val messages = ArrayList<PlayerMessage>()
        for (renderer in renderers) {
            messages.add(createMessage(renderer).setType(MSG_PLAY_RELEASED).send())
        }

        try {
            for (message in messages) {
                message.blockUntilDelivered()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}