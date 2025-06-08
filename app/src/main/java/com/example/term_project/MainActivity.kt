package com.example.term_project

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class MainActivity : AppCompatActivity(), MonthAdapter.OnMonthClickListener {

    private lateinit var yearTextView: TextView
    private lateinit var monthsRecyclerView: RecyclerView
    private lateinit var monthAdapter: MonthAdapter
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        updateYearDisplay()
    }

    private fun initViews() {
        yearTextView = findViewById(R.id.yearTextView)
        monthsRecyclerView = findViewById(R.id.monthsRecyclerView)
    }

    private fun setupRecyclerView() {
        // GridLayoutManager를 사용하여 2열로 배치
        val layoutManager = GridLayoutManager(this, 2)
        monthsRecyclerView.layoutManager = layoutManager

        // 월 데이터 생성 (1월부터 12월까지)
        val months = mutableListOf<Month>()
        val monthNames = arrayOf(
            "1", "2", "3", "4", "5", "6",
            "7", "8", "9", "10", "11", "12"
        )

        for (i in 1..12) {
            months.add(Month(i, monthNames[i-1], currentYear))
        }

        monthAdapter = MonthAdapter(months, this)
        monthsRecyclerView.adapter = monthAdapter
    }

    private fun updateYearDisplay() {
        yearTextView.text = "${currentYear}"
    }

    // MonthAdapter.OnMonthClickListener 인터페이스 구현
    override fun onMonthClick(month: Month) {
        val intent = Intent(this, DiaryListActivity::class.java)
        intent.putExtra("YEAR", month.year)
        intent.putExtra("MONTH", month.number)
        intent.putExtra("MONTH_NAME", month.name)
        startActivity(intent)
    }

    // 년도 변경 메서드 (나중에 버튼 등으로 호출 가능)
    private fun changeYear(newYear: Int) {
        currentYear = newYear
        updateYearDisplay()
        setupRecyclerView() // RecyclerView 갱신
    }
}

// 월 데이터 클래스
data class Month(
    val number: Int,    // 월 번호 (1-12)
    val name: String,   // 월 이름 ("1월", "2월" 등)
    val year: Int       // 년도
)