package com.moqan.mqplayer.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.moqan.mqplayer.MQMediaPlayer
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var player: MQMediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        player = MQMediaPlayer(this)

        val surfaceView = findViewById<SurfaceView>(R.id.sv_video)
        player.setDataSource(Uri.fromFile(File("/sdcard/lemon/123.mkv")))
        player.setSurfaceView(surfaceView)
        player.prepare()
        player.start()

    }
}
