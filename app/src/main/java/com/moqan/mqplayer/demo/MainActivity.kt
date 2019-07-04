package com.moqan.mqplayer.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.moqan.mqplayer.IMediaPlayer
import com.moqan.mqplayer.MQMediaPlayer
import java.io.File

class MainActivity : AppCompatActivity() {
    private val TAG = "mainActivity"
    private lateinit var player: MQMediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        player = MQMediaPlayer(this)

        val surfaceView = findViewById<SurfaceView>(R.id.sv_video)
        player.seekTo(1422580)

        player.setDataSource(Uri.fromFile(File("/sdcard/lemon/123.mkv")))

        player.setDisplay(surfaceView)

        player.addListener(object : IMediaPlayer.EventListener {
            override fun onPlaybackStateChanged(playingWhenReady: Boolean, playbackState: Int) {
                super.onPlaybackStateChanged(playingWhenReady, playbackState)
                Log.i(TAG, "onSeekComplete:$playingWhenReady,$playbackState")
            }

            override fun onSeekComplete(positionAfterSeek: Long) {
                super.onSeekComplete(positionAfterSeek)
                Log.i(TAG, "onSeekComplete:$positionAfterSeek")
            }

            override fun onRenderedFirstFrame() {
                super.onRenderedFirstFrame()
                Log.i(TAG, "onRenderedFirstFrame")
            }

            override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
                super.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio)
                Log.i(TAG, "onVideoSizeChanged:($width, $height)")
            }

            override fun onDurationChanged(duration: Long) {
                Log.i(TAG, "duration：${player.getDuration()}")
            }
        })

        findViewById<Button>(R.id.btn_pause).setOnClickListener {
            player.setPlayWhenReady(false)

        }

        findViewById<Button>(R.id.btn_resume).setOnClickListener {
            player.setPlayWhenReady(true)
        }

        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            player.reset()
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            player.stop()
        }

        findViewById<Button>(R.id.btn_prepare).setOnClickListener {
            player.prepare()
        }
    }
}
