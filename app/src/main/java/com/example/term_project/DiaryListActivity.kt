package com.example.term_project

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
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
                    Log.e("DiaryList", "Error loading diary entries", e)
                }
            }
        }

        private fun setupFab() {
            fabAddDiary.setOnClickListener {
                // 새 다이어리 작성 액티비티로 이동
                // val intent = Intent(this, WriteDiaryActivity::class.java)
                // intent.putExtra("YEAR", year)
                // intent.putExtra("MONTH", month)
                // startActivity(intent)

                // 임시로 토스트 메시지 표시
                android.widget.Toast.makeText(this, "새 일기 작성", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        private suspend fun getDiaryEntriesForMonth(year: Int, month: Int): List<DiaryEntry> = suspendCoroutine { continuation ->
            val currentUserId = auth.currentUser?.uid

            // 해당 월의 시작과 끝 날짜 계산
            val startOfMonth = getStartOfMonth(year, month)
            val startOfNextMonth = getStartOfMonth(year, month + 1)

            db.collection("Notes")
                .whereEqualTo("user", currentUserId)
                .whereGreaterThanOrEqualTo("created_at", startOfMonth)
                .whereLessThan("created_at", startOfNextMonth)
                .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    val diaryList = documents.mapNotNull { document ->
                        try {
                            val diary = document.toObject(DiaryEntry::class.java)
                            diary.id = document.id
                            diary
                        } catch (e: Exception) {
                            Log.e("Firestore", "Error converting document: ${document.id}", e)
                            null
                        }
                    }
                    continuation.resume(diaryList)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

    // 월의 시작 날짜 계산 함수
        private fun getStartOfMonth(year: Int, month: Int): Timestamp {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1) // Calendar.MONTH는 0부터 시작
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return Timestamp(calendar.time)
        }


        override fun onDiaryClick(diary: DiaryEntry) {
            // 다이어리 상세보기 또는 편집 액티비티로 이동
            android.widget.Toast.makeText(this, "${diary.title} 클릭됨", android.widget.Toast.LENGTH_SHORT).show()
        }

        override fun onSupportNavigateUp(): Boolean {
            finish()
            return true
        }
}

// 다이어리 엔트리 데이터 클래스
data class DiaryEntry(
    var id: String = "",
    val title: String = "",
    val body: String = "",
    val llm_used: Boolean,
    val user: String = "",
    val created_at: Timestamp? = null
) {
    val date: String
        get() = created_at?.let { timestamp ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(timestamp.toDate())
        } ?: ""
}
