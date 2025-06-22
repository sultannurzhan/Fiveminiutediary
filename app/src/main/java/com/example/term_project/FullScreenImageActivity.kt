package com.example.term_project

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.File

class FullScreenImageActivity : AppCompatActivity() {
    
    private lateinit var imageView: ImageView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_fullscreen_image)
            
            imageView = findViewById<ImageView>(R.id.fullScreenImageView)
            val imagePath = intent.getStringExtra("IMAGE_PATH")
            
            Log.d("FullScreenImage", "Starting activity with image path: $imagePath")
            
            if (imagePath.isNullOrEmpty()) {
                Log.e("FullScreenImage", "Image path is null or empty")
                showErrorAndFinish("No image to display")
                return
            }
            
            setupClickListeners()
            loadImage(imagePath)
            
        } catch (e: Exception) {
            Log.e("FullScreenImage", "Error in onCreate: ${e.message}", e)
            showErrorAndFinish("Error loading image: ${e.message}")
        }
    }
    
    private fun setupClickListeners() {
        // Close on image click
        imageView.setOnClickListener {
            Log.d("FullScreenImage", "Image clicked, finishing activity")
            finish()
        }
    }
    
    private fun loadImage(imagePath: String) {
        try {
            // Check if file exists
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e("FullScreenImage", "Image file does not exist: $imagePath")
                showErrorAndFinish("Image file not found")
                return
            }
            
            if (!imageFile.canRead()) {
                Log.e("FullScreenImage", "Cannot read image file: $imagePath")
                showErrorAndFinish("Cannot access image file")
                return
            }
            
            Log.d("FullScreenImage", "Loading image: $imagePath, size: ${imageFile.length()} bytes")
            
            // Load image with Glide with better error handling
            Glide.with(this)
                .load(imageFile)
                .apply(RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .override(2048, 2048) // Limit size to prevent memory issues
                    .fitCenter()
                )
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_dialog_alert)
                .into(imageView)
            
            Log.d("FullScreenImage", "Image load request submitted successfully")
            
        } catch (e: Exception) {
            Log.e("FullScreenImage", "Error loading image: ${e.message}", e)
            showErrorAndFinish("Failed to load image")
        }
    }
    
    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
