package com.moqan.mqplayer.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
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

        Toast.makeText(this, "文件浏览器播中放视频时，请选择用MQPlayer打开", Toast.LENGTH_LONG).show()
        finish()
    }
}
