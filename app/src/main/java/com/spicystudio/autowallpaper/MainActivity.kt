package com.spicystudio.autowallpaper

import android.Manifest
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridView
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
    private val PREFS_NAME = "AutoWallpaperPrefs"
    private val PREF_DONT_SHOW_AGAIN = "dontShowAgain"
    private val imageFiles = mutableListOf<File>()
    private lateinit var gridView: GridView
    private lateinit var addButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridView = findViewById(R.id.gridView)
        addButton = findViewById(R.id.addButton)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!sharedPreferences.getBoolean(PREF_DONT_SHOW_AGAIN, false)) {
            showUsageDialog()
        }

        checkAndRequestPermissions()

        gridView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedImage = imageFiles[position]
            val intent = Intent(this, FullscreenImageActivity::class.java)
            intent.putExtra("imagePath", selectedImage.absolutePath)
            startActivity(intent)
        }

        gridView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val selectedImage = imageFiles[position]
            AlertDialog.Builder(this)
                .setTitle("Delete Image")
                .setMessage("Are you sure you want to delete this image?")
                .setPositiveButton("Yes") { _, _ ->
                    selectedImage.delete()
                    imageFiles.removeAt(position)
                    (gridView.adapter as ImageAdapter).notifyDataSetChanged()
                    Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("No", null)
                .show()
            true
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(ScreenOffReceiver(), filter)

        addButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            galleryLauncher.launch(intent)
        }
    }

    private fun showUsageDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_usage, null)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.checkBoxDontShowAgain)
        val buttonConfirm = dialogView.findViewById<Button>(R.id.buttonConfirm)

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Usage Instructions")
            .setView(dialogView)
            .create()

        buttonConfirm.setOnClickListener {
            if (checkBox.isChecked) {
                sharedPreferences.edit().putBoolean(PREF_DONT_SHOW_AGAIN, true).apply()
            }
            alertDialog.dismiss()
        }

        alertDialog.show()
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
            val clipData = result.data!!.clipData
            val destinationFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutoWallpaper")
            if (!destinationFolder.exists()) {
                destinationFolder.mkdirs()
            }

            try {
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val selectedImageUri = clipData.getItemAt(i).uri
                        saveImageToFolder(selectedImageUri, destinationFolder)
                    }
                } else {
                    val selectedImageUri: Uri? = result.data!!.data
                    if (selectedImageUri != null) {
                        saveImageToFolder(selectedImageUri, destinationFolder)
                    }
                }
                (gridView.adapter as ImageAdapter).notifyDataSetChanged()
                Toast.makeText(this, "Images Added", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("MainActivity", "galleryLauncher: Failed to add images", e)
                Toast.makeText(this, "Failed to add images", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("MainActivity", "galleryLauncher: Image selection failed or canceled")
            Toast.makeText(this, "Image selection failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToFolder(selectedImageUri: Uri, destinationFolder: File) {
        val destinationFile = File(destinationFolder, "${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(selectedImageUri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        imageFiles.add(destinationFile)
    }
}
