/*
package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.ext.ffmpeg.video.SoftVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;

*/
/**
 * @author joffychim
 * @since 18/2/9
 *//*

public class SimpleRenderersFactory extends DefaultRenderersFactory {
    public SimpleRenderersFactory(Context context) {
        super(context);
    }

    public SimpleRenderersFactory(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        super(context, drmSessionManager);
    }

    public SimpleRenderersFactory(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode) {
        super(context, drmSessionManager, extensionRendererMode);
    }

    public SimpleRenderersFactory(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode, long allowedVideoJoiningTimeMs) {
        super(context, drmSessionManager, extensionRendererMode, allowedVideoJoiningTimeMs);
    }

    @Override
    protected void buildVideoRenderers(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, long allowedVideoJoiningTimeMs, Handler eventHandler, VideoRendererEventListener eventListener, int extensionRendererMode, ArrayList<Renderer> out) {
        BaseRenderer ffmpegRenderer = new SoftVideoRenderer(true,
                allowedVideoJoiningTimeMs, eventHandler, eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
                drmSessionManager, false);
        out.add(ffmpegRenderer);

        super.buildVideoRenderers(context, drmSessionManager, allowedVideoJoiningTimeMs, eventHandler, eventListener, extensionRendererMode, out);
    }
}
*/
