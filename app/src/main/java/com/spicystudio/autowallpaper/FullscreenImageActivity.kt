package com.spicystudio.autowallpaper


import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class FullscreenImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_image)

        val imageView: ImageView = findViewById(R.id.fullscreenImageView)
        val imagePath = intent.getStringExtra("imagePath")

        imagePath?.let {
            val bitmap = BitmapFactory.decodeFile(it)
            imageView.setImageBitmap(bitmap)
        }
    }
}
