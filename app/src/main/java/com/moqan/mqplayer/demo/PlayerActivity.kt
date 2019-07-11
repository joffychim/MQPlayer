package com.moqan.mqplayer.demo

import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.SurfaceView
import com.google.android.exoplayer2.ext.ffmpeg.video.FrameScaleType
import com.moqan.mqplayer.MQMediaPlayer

class PlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val surfaceView: SurfaceView = findViewById(R.id.sf_holder)
        val player = MQMediaPlayer(this)
        player.setBackgroundColor(Color.argb(255, 255, 255, 255))
        player.setScaleType(FrameScaleType.FIT_Y)
        player.setLooping(true)

        player.setPlayWhenReady(true)
        player.setDisplay(surfaceView)
        player.setDataSource(Uri.parse(intent.dataString))
        player.prepare()
    }
}
