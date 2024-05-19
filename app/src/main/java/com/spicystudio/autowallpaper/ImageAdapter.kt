package com.spicystudio.autowallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import java.io.File

class ImageAdapter(private val context: Context, private val imageFiles: List<File>) : BaseAdapter() {

    override fun getCount(): Int {
        return imageFiles.size
    }

    override fun getItem(position: Int): Any {
        return imageFiles[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val imageView: ImageView = convertView as? ImageView ?: ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(200, 200)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val bitmap = BitmapFactory.decodeFile(imageFiles[position].absolutePath)
        imageView.setImageBitmap(bitmap)
        return imageView
    }
}
