package com.moqan.mqplayer.demo

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.view.View.*
import com.google.android.exoplayer2.ext.ffmpeg.video.FrameScaleType
import com.moqan.mqplayer.MQMediaPlayer

class PlayerActivity : Activity() {
    private lateinit var player: MQMediaPlayer


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        actionBar?.hide()
        val v = window.decorView
        val origFlags = v.systemUiVisibility
        // SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION 这个的意思是 不隐藏导航栏，只是我插进入
        val immersiveFlags = origFlags or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        v.systemUiVisibility = immersiveFlags

        val surfaceView: SurfaceView = findViewById(R.id.sf_holder)
        player = MQMediaPlayer(this)
        player.setBackgroundColor(Color.argb(0, 255, 255, 255))
        player.setScaleType(FrameScaleType.FIT_CENTER)
        player.setLooping(true)

        player.setDisplay(surfaceView)
        player.setDataSource(Uri.parse(intent.dataString))
        player.prepare()
    }

    override fun onResume() {
        super.onResume()

        player.setPlayWhenReady(true)

    }

    override fun onPause() {
        super.onPause()

        player.setPlayWhenReady(false)
    }

    override fun onDestroy() {
        super.onDestroy()

        player.release()
    }
}
