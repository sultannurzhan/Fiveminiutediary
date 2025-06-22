package com.example.term_project

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DiaryDetailActivity : AppCompatActivity() {
    
    private lateinit var dateTextView: TextView
    private lateinit var titleEditText: EditText
    private lateinit var bodyEditText: EditText
    private lateinit var backButton: Button
    private lateinit var deleteButton: Button
    private lateinit var saveButton: Button
    private lateinit var generateTitleButton: Button
    private lateinit var aiResponseTextView: TextView
    private lateinit var askAiButton: Button
    private lateinit var acceptAiButton: Button
    private lateinit var rejectAiButton: Button
    private lateinit var addImageButton: Button
    private lateinit var imageRecyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    
    private val imageList = mutableListOf<String>()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val aiService = AiService()
    
    private var diaryId: String? = null
    private var isEditing = false
    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary_detail)
        
        initViews()
        loadDiaryData()
        setupButtons()
    }
    
    private fun initViews() {
        dateTextView = findViewById(R.id.dateTextView)
        titleEditText = findViewById(R.id.titleEditText)
        bodyEditText = findViewById(R.id.bodyEditText)
        backButton = findViewById(R.id.backButton)
        deleteButton = findViewById(R.id.deleteButton)
        saveButton = findViewById(R.id.saveButton)
        generateTitleButton = findViewById(R.id.generateTitleButton)
        aiResponseTextView = findViewById(R.id.aiResponseTextView)
        askAiButton = findViewById(R.id.askAiButton)
        acceptAiButton = findViewById(R.id.acceptAiButton)
        rejectAiButton = findViewById(R.id.rejectAiButton)
        addImageButton = findViewById(R.id.addImageButton)
        imageRecyclerView = findViewById(R.id.imageRecyclerView)
        
        // Set current date
        val currentDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        dateTextView.text = currentDate
        
        // Setup image RecyclerView
        setupImageRecyclerView()
        
        // Improve keyboard handling for body EditText
        bodyEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Ensure the EditText is visible when keyboard appears
                bodyEditText.post {
                    bodyEditText.requestFocus()
                }
            }
        }
    }
    
    private fun loadDiaryData() {
        diaryId = intent.getStringExtra("DIARY_ID")
        currentYear = intent.getIntExtra("YEAR", Calendar.getInstance().get(Calendar.YEAR))
        currentMonth = intent.getIntExtra("MONTH", Calendar.getInstance().get(Calendar.MONTH) + 1)
        
        if (diaryId != null) {
            // Editing existing diary
            isEditing = true
            val title = intent.getStringExtra("DIARY_TITLE") ?: ""
            val body = intent.getStringExtra("DIARY_BODY") ?: ""
            val images = intent.getStringArrayListExtra("DIARY_IMAGES") ?: arrayListOf()
            
            titleEditText.setText(title)
            bodyEditText.setText(body)
            
            // Load existing images with validation
            imageList.clear()
            images.forEach { imagePath ->
                // Only add valid image paths that exist
                if (imagePath.isNotEmpty()) {
                    val file = File(imagePath)
                    if (file.exists() && file.canRead()) {
                        imageList.add(imagePath)
                    } else {
                        Log.w("DiaryDetail", "Skipping invalid image path: $imagePath")
                    }
                }
            }
            imageAdapter.notifyDataSetChanged()
            
            // Show delete button only when editing existing diary
            deleteButton.visibility = View.VISIBLE
        } else {
            // Creating new diary
            isEditing = false
            titleEditText.hint = "Enter diary title"
            bodyEditText.hint = "Write your diary content here..."
            
            // Hide delete button when creating new diary
            deleteButton.visibility = View.GONE
        }
    }
    
    private fun setupButtons() {
        backButton.setOnClickListener {
            hideKeyboard()
            finish()
        }
        
        deleteButton.setOnClickListener {
            hideKeyboard()
            showDeleteConfirmationDialog()
        }
        
        saveButton.setOnClickListener {
            hideKeyboard()
            saveDiary()
        }
        
        generateTitleButton.setOnClickListener {
            generateTitleWithAi()
        }
        
        askAiButton.setOnClickListener {
            getAiExtension()
        }
        
        acceptAiButton.setOnClickListener {
            acceptAiExtension()
        }
        
        rejectAiButton.setOnClickListener {
            rejectAiExtension()
        }
        
        addImageButton.setOnClickListener {
            checkPermissionAndPickImage()
        }
    }
    
    private fun saveDiary() {
        var title = titleEditText.text.toString().trim()
        val body = bodyEditText.text.toString().trim()
        
        if (body.isEmpty()) {
            Toast.makeText(this, "Please enter diary content", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(this@DiaryDetailActivity, "Login required", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Auto-generate title if empty
                var titleGeneratedByAi = false
                if (title.isEmpty()) {
                    Toast.makeText(this@DiaryDetailActivity, "AI is generating title...", Toast.LENGTH_SHORT).show()
                    title = aiService.generateTitle(body)
                    titleEditText.setText(title)
                    titleGeneratedByAi = true
                }
                
                val currentTime = Timestamp.now()
                
                if (isEditing && diaryId != null) {
                    // Update existing diary
                    val updates = hashMapOf<String, Any>(
                        "title" to title,
                        "body" to body,
                        "updated_at" to currentTime,
                        "images" to imageList
                    )
                    
                    // Add llm_used flag if title was generated by AI
                    if (titleGeneratedByAi) {
                        updates["llm_used"] = true
                    }
                    
                    db.collection("NOTE")
                        .document(diaryId!!)
                        .update(updates)
                        .await()
                    
                    Toast.makeText(this@DiaryDetailActivity, "Diary updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    // Create new diary
                    val monthString = String.format("%04d-%02d", currentYear, currentMonth)
                    
                    val diaryEntry = hashMapOf(
                        "title" to title,
                        "body" to body,
                        "user" to currentUser.uid,
                        "created_at" to currentTime,
                        "updated_at" to currentTime,
                        "llm_used" to titleGeneratedByAi,
                        "month" to monthString,
                        "images" to imageList
                    )
                    
                    db.collection("NOTE")
                        .add(diaryEntry)
                        .await()
                    
                    Toast.makeText(this@DiaryDetailActivity, "Diary saved successfully", Toast.LENGTH_SHORT).show()
                }
                
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this@DiaryDetailActivity, "Error saving diary: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Diary")
            .setMessage("Are you sure you want to delete this diary? Deleted diaries cannot be recovered.")
            .setPositiveButton("Delete") { _, _ ->
                deleteDiary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteDiary() {
        if (diaryId == null) {
            Toast.makeText(this, "No diary to delete", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(this@DiaryDetailActivity, "Login required", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Delete the diary from Firestore
                db.collection("NOTE")
                    .document(diaryId!!)
                    .delete()
                    .await()
                
                Toast.makeText(this@DiaryDetailActivity, "Diary deleted successfully", Toast.LENGTH_SHORT).show()
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this@DiaryDetailActivity, "Error deleting diary: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun generateTitleWithAi() {
        val body = bodyEditText.text.toString().trim()
        if (body.isEmpty()) {
            Toast.makeText(this, "Please write diary content first to generate a title", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                generateTitleButton.isEnabled = false
                generateTitleButton.text = "..."
                
                val generatedTitle = aiService.generateTitle(body)
                titleEditText.setText(generatedTitle)
                
                Toast.makeText(this@DiaryDetailActivity, "AI generated a title!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DiaryDetailActivity, "Error generating title: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                generateTitleButton.isEnabled = true
                generateTitleButton.text = "🤖"
            }
        }
    }
    
    private var currentAiExtension: String = ""
    
    // Activity result launcher for image picking
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveImageLocally(uri)
            }
        }
    }
    
    private fun getAiExtension() {
        val body = bodyEditText.text.toString().trim()
        if (body.isEmpty()) {
            Toast.makeText(this, "Please write diary content first to get AI help", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                askAiButton.isEnabled = false
                askAiButton.text = "Extending..."
                aiResponseTextView.text = "AI is extending your diary..."
                
                val extension = aiService.getAiSuggestion(body)
                currentAiExtension = extension
                aiResponseTextView.text = extension
                
                // Show accept/reject buttons
                showAcceptRejectButtons(true)
                
            } catch (e: Exception) {
                aiResponseTextView.text = "Error getting AI extension. Please try again later. ${e.message}"
                showAcceptRejectButtons(false)
            } finally {
                askAiButton.isEnabled = true
                askAiButton.text = "AI Help"
            }
        }
    }
    
    private fun acceptAiExtension() {
        if (currentAiExtension.isNotEmpty()) {
            val currentBody = bodyEditText.text.toString().trim()
            val newBody = if (currentBody.isEmpty()) {
                currentAiExtension
            } else {
                "$currentBody\n\n$currentAiExtension"
            }
            bodyEditText.setText(newBody)
            Toast.makeText(this, "AI extension added to your diary!", Toast.LENGTH_SHORT).show()
            clearAiResponse()
        }
    }
    
    private fun rejectAiExtension() {
        clearAiResponse()
        Toast.makeText(this, "AI extension rejected", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearAiResponse() {
        currentAiExtension = ""
        aiResponseTextView.text = "Write your diary and get AI help to extend it!"
        showAcceptRejectButtons(false)
    }
    
    private fun showAcceptRejectButtons(show: Boolean) {
        if (show) {
            acceptAiButton.visibility = View.VISIBLE
            rejectAiButton.visibility = View.VISIBLE
            askAiButton.visibility = View.GONE
        } else {
            acceptAiButton.visibility = View.GONE
            rejectAiButton.visibility = View.GONE
            askAiButton.visibility = View.VISIBLE
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
    
    private fun setupImageRecyclerView() {
        imageAdapter = ImageAdapter(
            images = imageList,
            onImageClick = { imagePath ->
                showFullScreenImage(imagePath)
            },
            onImageDelete = { position ->
                showDeleteImageDialog(position)
            }
        )
        
        imageRecyclerView.layoutManager = GridLayoutManager(this, 3)
        imageRecyclerView.adapter = imageAdapter
    }
    
    private fun checkPermissionAndPickImage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 
                    PERMISSION_REQUEST_CODE
                )
            } else {
                openImagePicker()
            }
        } else {
            // Below Android 13 uses READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, 
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 
                    PERMISSION_REQUEST_CODE
                )
            } else {
                openImagePicker()
            }
        }
    }
    
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }
    
    private fun saveImageLocally(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                // Create app directory if it doesn't exist
                val appDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiaryImages")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                
                // Generate unique filename
                val timestamp = System.currentTimeMillis()
                val filename = "diary_image_$timestamp.jpg"
                val imageFile = File(appDir, filename)
                
                // Save bitmap to file
                val outputStream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                outputStream.close()
                
                // Add to adapter
                imageAdapter.addImage(imageFile.absolutePath)
                
                Toast.makeText(this@DiaryDetailActivity, "Image added successfully", Toast.LENGTH_SHORT).show()
                
            } catch (e: IOException) {
                Toast.makeText(this@DiaryDetailActivity, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showFullScreenImage(imagePath: String) {
        try {
            Log.d("DiaryDetail", "Opening full screen image: $imagePath")
            
            if (imagePath.isBlank()) {
                Toast.makeText(this, "Invalid image path", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Check if file exists before opening
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e("DiaryDetail", "Image file does not exist: $imagePath")
                Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (!imageFile.canRead()) {
                Log.e("DiaryDetail", "Cannot read image file: $imagePath")
                Toast.makeText(this, "Cannot access image file", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d("DiaryDetail", "Image file exists and is readable, size: ${imageFile.length()} bytes")
            
            val intent = Intent(this, FullScreenImageActivity::class.java)
            intent.putExtra("IMAGE_PATH", imagePath)
            
            // Add flags to ensure proper activity handling
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            startActivity(intent)
            
            Log.d("DiaryDetail", "Successfully started FullScreenImageActivity")
            
        } catch (e: SecurityException) {
            Log.e("DiaryDetail", "Security exception opening image: ${e.message}", e)
            Toast.makeText(this, "Permission denied accessing image", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("DiaryDetail", "Error opening full screen image: ${e.message}", e)
            Toast.makeText(this, "Error opening image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteImageDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Image")
            .setMessage("Are you sure you want to delete this image?")
            .setPositiveButton("Delete") { _, _ ->
                deleteImage(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteImage(position: Int) {
        if (position in 0 until imageList.size) {
            val imagePath = imageList[position]
            
            // Delete file from storage
            val file = File(imagePath)
            if (file.exists()) {
                file.delete()
            }
            
            // Remove from adapter
            imageAdapter.removeImage(position)
            Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<out String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker()
            } else {
                Toast.makeText(this, "Permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}