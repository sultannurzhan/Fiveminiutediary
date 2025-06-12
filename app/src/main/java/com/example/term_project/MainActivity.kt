package com.example.term_project

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import java.util.*

class MainActivity : AppCompatActivity(), MonthAdapter.OnMonthClickListener, NavigationView.OnNavigationItemSelectedListener {

    private lateinit var yearTextView: TextView
    private lateinit var monthsRecyclerView: RecyclerView
    private lateinit var monthAdapter: MonthAdapter
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check if user is authenticated
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Not authenticated, go to LoginActivity
            val intent = Intent(this, com.example.term_project.auth.LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
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

        for (i in 1..12) {
            months.add(Month(i, i.toString(), currentYear))
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
        startActivity(intent)
    }

    // 년도 변경 메서드 (나중에 버튼 등으로 호출 가능)
    private fun changeYear(newYear: Int) {
        currentYear = newYear
        updateYearDisplay()
        setupRecyclerView() // RecyclerView 갱신
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_logout -> {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, com.example.term_project.auth.LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return true
            }
        }
        drawerLayout.closeDrawer(Gravity.LEFT)
        return true
    }
}

// 월 데이터 클래스
data class Month(
    val number: Int,    // 월 번호 (1-12)
    val name: String,   // 월 이름 ("1월", "2월" 등)
    val year: Int       // 년도
)