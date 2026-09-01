package com.example.manualreply

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class ImageViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val url = intent.getStringExtra("imageUrl") ?: ""
        val imageView = findViewById<ImageView>(R.id.fullImage)
        Glide.with(this).load(url).into(imageView)

        findViewById<ImageButton>(R.id.closeButton).setOnClickListener { finish() }
        imageView.setOnClickListener { finish() } // tap anywhere on the image also closes
    }
}
