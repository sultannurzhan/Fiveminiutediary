package com.example.term_project

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class DiaryListActivity : AppCompatActivity(), DiaryAdapter.OnDiaryClickListener {

        private lateinit var titleTextView: TextView
        private lateinit var diaryRecyclerView: RecyclerView
        private lateinit var fabAddDiary: FloatingActionButton
        private lateinit var diaryAdapter: DiaryAdapter
        private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
        private val auth = FirebaseAuth.getInstance()
        private var year: Int = 0
        private var month: Int = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_diary_list)

            getIntentData()
            initViews()
            setupRecyclerView()
            setupTitle()
            setupFab()

            loadDiaryEntries()
        }

        private fun getIntentData() {
            year = intent.getIntExtra("YEAR", 2024)
            month = intent.getIntExtra("MONTH", 1)
        }

        private fun initViews() {
            titleTextView = findViewById(R.id.titleTextView)
            diaryRecyclerView = findViewById(R.id.diaryRecyclerView)
            fabAddDiary = findViewById(R.id.fabAddDiary)

            // 뒤로가기 버튼 활성화
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        private fun setupTitle() {
            titleTextView.text = "${year}/ ${month} diaries"
        }

        private fun setupRecyclerView() {
            diaryRecyclerView.layoutManager = LinearLayoutManager(this)
            diaryAdapter = DiaryAdapter(mutableListOf(), this)
            diaryRecyclerView.adapter = diaryAdapter
        }

        private fun loadDiaryEntries() {
            lifecycleScope.launch {
                try {
                    val diaryEntries = getDiaryEntriesForMonth(year, month)
                    diaryAdapter.updateEntries(diaryEntries) // 어댑터 업데이트
                } catch (e: Exception) {
                    // Error handling could be added here if needed
                }
            }
        }

        private fun setupFab() {
            fabAddDiary.setOnClickListener {
                // Navigate to DiaryDetailActivity for creating new diary
                val intent = android.content.Intent(this, DiaryDetailActivity::class.java)
                intent.putExtra("YEAR", year)
                intent.putExtra("MONTH", month)
                startActivity(intent)
            }
        }
        
        private suspend fun getDiaryEntriesForMonth(year: Int, month: Int):
                List<DiaryEntry> = suspendCoroutine { continuation -> val currentUserId = auth.currentUser?.uid

            val monthString = String.format("%04d-%02d", year, month)

            db.collection("NOTE")
                .whereEqualTo("user", currentUserId)
                .whereEqualTo("month", monthString)
                .get()
                .addOnSuccessListener { documents ->
                    val diaryList = documents.mapNotNull { document ->
                        try {
                            val diary = document.toObject(DiaryEntry::class.java)
                            diary.id = document.id
                            diary
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.created_at?.toDate() } // Sort in memory instead
                    continuation.resume(diaryList)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

        override fun onDiaryClick(diary: DiaryEntry) {
            // Navigate to DiaryDetailActivity for viewing/editing
            val intent = android.content.Intent(this, DiaryDetailActivity::class.java)
            intent.putExtra("DIARY_ID", diary.id)
            intent.putExtra("DIARY_TITLE", diary.title)
            intent.putExtra("DIARY_BODY", diary.body)
            intent.putExtra("LLM_USED", diary.llm_used)
            intent.putExtra("YEAR", year)
            intent.putExtra("MONTH", month)
            intent.putStringArrayListExtra("DIARY_IMAGES", ArrayList(diary.images))
            startActivity(intent)
        }
        
        override fun onDiaryLongClick(diary: DiaryEntry) {
            // Show delete confirmation dialog on long click
            showDeleteConfirmationDialog(diary)
        }
        
        private fun showDeleteConfirmationDialog(diary: DiaryEntry) {
            AlertDialog.Builder(this)
                .setTitle("일기 삭제")
                .setMessage("'${diary.title}' 일기를 삭제하시겠습니까? 삭제된 일기는 복구할 수 없습니다.")
                .setPositiveButton("삭제") { _, _ ->
                    deleteDiary(diary)
                }
                .setNegativeButton("취소", null)
                .show()
        }
        
        private fun deleteDiary(diary: DiaryEntry) {
            lifecycleScope.launch {
                try {
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        Toast.makeText(this@DiaryListActivity, "Login required", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    
                    // Delete the diary from Firestore
                    db.collection("NOTE")
                        .document(diary.id)
                        .delete()
                        .await()
                    
                    Toast.makeText(this@DiaryListActivity, "Diary deleted successfully", Toast.LENGTH_SHORT).show()
                    
                    // Refresh the diary list
                    loadDiaryEntries()
                    
                } catch (e: Exception) {
                    Toast.makeText(this@DiaryListActivity, "Error deleting diary: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun onSupportNavigateUp(): Boolean {
            finish()
            return true
        }

        override fun onResume() {
            super.onResume()
            // Refresh the diary list when returning from DiaryDetailActivity
            loadDiaryEntries()
        }
}
