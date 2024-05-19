package com.spicystudio.autowallpaper

import android.Manifest
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Random

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSIONS = 1
    private val imageFiles = mutableListOf<File>()
    private lateinit var previewImageView: ImageView
    private lateinit var gridView: GridView
    private lateinit var addButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewImageView = findViewById(R.id.previewImageView)
        gridView = findViewById(R.id.gridView)
        addButton = findViewById(R.id.addButton)

        Log.d("MainActivity", "onCreate: Initializing the app")

        checkAndRequestPermissions()

        gridView.setOnItemClickListener { parent, view, position, id ->
            val selectedImage = imageFiles[position]
            val bitmap = BitmapFactory.decodeFile(selectedImage.absolutePath)
            previewImageView.setImageBitmap(bitmap)
            changeWallpaper(bitmap)
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(ScreenOffReceiver(), filter)

        addButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        Log.d("MainActivity", "checkAndRequestPermissions: Checking permissions")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "checkAndRequestPermissions: Requesting READ_MEDIA_VISUAL_USER_SELECTED and WRITE_EXTERNAL_STORAGE")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSIONS)
            } else {
                loadImagesFromFolder()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "checkAndRequestPermissions: Requesting READ_MEDIA_IMAGES and WRITE_EXTERNAL_STORAGE")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSIONS)
            } else {
                loadImagesFromFolder()
            }
        } else { // Android 12及以下版本
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "checkAndRequestPermissions: Requesting READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSIONS)
            } else {
                loadImagesFromFolder()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "onRequestPermissionsResult: Permissions granted")
                loadImagesFromFolder()
            } else {
                Log.d("MainActivity", "onRequestPermissionsResult: Permissions denied")
                Toast.makeText(this, "Permissions are required to use this app", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadImagesFromFolder() {
        Log.d("MainActivity", "loadImagesFromFolder: Loading images from folder")
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutoWallpaper")
        if (folder.exists() && folder.isDirectory) {
            val files = folder.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".png"))) {
                        Log.d("MainActivity", "loadImagesFromFolder: Found image file: ${file.name}")
                        imageFiles.add(file)
                    }
                }
            }
        }
        gridView.adapter = ImageAdapter(this, imageFiles)
    }

    private fun changeWallpaper(bitmap: Bitmap) {
        val wallpaperManager = WallpaperManager.getInstance(this)
        try {
            wallpaperManager.setBitmap(bitmap)
            Toast.makeText(this, "Wallpaper Set", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("MainActivity", "changeWallpaper: Failed to set wallpaper", e)
        }
    }

    private inner class ScreenOffReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_SCREEN_OFF == intent.action) {
                Log.d("MainActivity", "ScreenOffReceiver: Screen turned off, changing wallpaper")
                val random = Random()
                val randomIndex = random.nextInt(imageFiles.size)
                val randomImage = imageFiles[randomIndex]
                val bitmap = BitmapFactory.decodeFile(randomImage.absolutePath)
                changeWallpaper(bitmap)
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val selectedImageUri: Uri? = result.data!!.data
            if (selectedImageUri != null) {
                val destinationFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutoWallpaper")
                if (!destinationFolder.exists()) {
                    destinationFolder.mkdirs()
                }
                val destinationFile = File(destinationFolder, "${System.currentTimeMillis()}.jpg")
                try {
                    Log.d("MainActivity", "galleryLauncher: Copying selected image to destination folder")
                    contentResolver.openInputStream(selectedImageUri)?.use { inputStream ->
                        FileOutputStream(destinationFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    imageFiles.add(destinationFile)
                    (gridView.adapter as ImageAdapter).notifyDataSetChanged()
                    Toast.makeText(this, "Image Added", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e("MainActivity", "galleryLauncher: Failed to add image", e)
                    Toast.makeText(this, "Failed to add image", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("MainActivity", "galleryLauncher: Selected image URI is null")
            }
        } else {
            Log.d("MainActivity", "galleryLauncher: Image selection failed or canceled")
            Toast.makeText(this, "Image selection failed", Toast.LENGTH_SHORT).show()
        }
    }
}
