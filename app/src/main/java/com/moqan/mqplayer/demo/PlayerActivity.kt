package com.moqan.mqplayer.demo

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.SurfaceView
import com.moqan.mqplayer.MQMediaPlayer

class PlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val surfaceView: SurfaceView = findViewById(R.id.sf_holder)
        val player = MQMediaPlayer(this)
        player.setPlayWhenReady(true)
        player.setDisplay(surfaceView)
        player.setDataSource(Uri.parse(intent.dataString))
        player.prepare()
    }
}
