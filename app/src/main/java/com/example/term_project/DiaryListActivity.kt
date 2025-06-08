package com.example.term_project

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DiaryListActivity : AppCompatActivity(), DiaryAdapter.OnDiaryClickListener {

    private lateinit var titleTextView: TextView
    private lateinit var diaryRecyclerView: RecyclerView
    private lateinit var fabAddDiary: FloatingActionButton
    private lateinit var diaryAdapter: DiaryAdapter

    private var year: Int = 0
    private var month: Int = 0
    private var monthName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary_list)

        getIntentData()
        initViews()
        setupRecyclerView()
        setupTitle()
        setupFab()
    }

    private fun getIntentData() {
        year = intent.getIntExtra("YEAR", 2024)
        month = intent.getIntExtra("MONTH", 1)
        monthName = intent.getStringExtra("MONTH_NAME") ?: "1월"
    }

    private fun initViews() {
        titleTextView = findViewById(R.id.titleTextView)
        diaryRecyclerView = findViewById(R.id.diaryRecyclerView)
        fabAddDiary = findViewById(R.id.fabAddDiary)

        // 뒤로가기 버튼 활성화
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupTitle() {
        titleTextView.text = "${year}년 ${monthName} 일기"
    }

    private fun setupRecyclerView() {
        diaryRecyclerView.layoutManager = LinearLayoutManager(this)

        // 샘플 다이어리 데이터 (실제로는 데이터베이스에서 가져와야 함)
        val diaryEntries = getDiaryEntriesForMonth(year, month)

        diaryAdapter = DiaryAdapter(diaryEntries, this)
        diaryRecyclerView.adapter = diaryAdapter
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

    // 실제로는 데이터베이스에서 해당 월의 다이어리를 가져오는 메서드
    private fun getDiaryEntriesForMonth(year: Int, month: Int): List<DiaryEntry> {
        // 샘플 데이터
        return listOf(
            DiaryEntry(
                id = 1,
                title = "오늘의 일기",
                content = "오늘은 정말 좋은 하루였다. 친구들과 만나서 즐거운 시간을 보냈고...",
                date = "${year}-${month.toString().padStart(2, '0')}-01",
                createdAt = System.currentTimeMillis()
            ),
            DiaryEntry(
                id = 2,
                title = "새로운 시작",
                content = "새로운 다이어리 앱을 만들기 시작했다. 설레는 마음으로...",
                date = "${year}-${month.toString().padStart(2, '0')}-05",
                createdAt = System.currentTimeMillis()
            ),
            DiaryEntry(
                id = 3,
                title = "감사한 하루",
                content = "오늘 하루도 무사히 지나갔다. 작은 것들에 감사하며...",
                date = "${year}-${month.toString().padStart(2, '0')}-10",
                createdAt = System.currentTimeMillis()
            )
        )
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
    val id: Long,
    val title: String,
    val content: String,
    val date: String,        // yyyy-MM-dd 형식
    val createdAt: Long      // 생성 시간 (timestamp)
)