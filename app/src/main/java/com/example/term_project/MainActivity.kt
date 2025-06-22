package com.example.term_project

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.term_project.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import java.util.*

class MainActivity : AppCompatActivity(), MonthAdapter.OnMonthClickListener, NavigationView.OnNavigationItemSelectedListener {
    private lateinit var yearTextView: TextView
    private lateinit var monthsRecyclerView: RecyclerView
    private lateinit var monthAdapter: MonthAdapter
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var prevYearButton: MaterialButton
    private lateinit var nextYearButton: MaterialButton
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check if user is authenticated
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Not authenticated, go to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
        
        // Setup drawer toggle
        setupDrawerToggle()
        
        initViews()
        setupRecyclerView()
        updateYearDisplay()
        setupYearNavigation()
        
        // Setup modern back button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    drawerLayout.closeDrawer(Gravity.LEFT)
                } else {
                    finish()
                }
            }
        })
    }

    private fun initViews() {
        yearTextView = findViewById(R.id.yearTextView)
        monthsRecyclerView = findViewById(R.id.monthsRecyclerView)
        prevYearButton = findViewById(R.id.prevYearButton)
        nextYearButton = findViewById(R.id.nextYearButton)
    }

    private fun setupDrawerToggle() {
        // Create a simple toolbar for the drawer toggle
        val toolbar = Toolbar(this)
        setSupportActionBar(toolbar)
        
        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupYearNavigation() {
        prevYearButton.setOnClickListener {
            if (currentYear > 1900) { // Reasonable lower bound
                changeYear(currentYear - 1)
            }
        }
        
        nextYearButton.setOnClickListener {
            if (currentYear < 2100) { // Reasonable upper bound
                changeYear(currentYear + 1)
            }
        }
    }

    private fun setupRecyclerView() {
        // GridLayoutManager를 사용하여 2열로 배치
        val layoutManager = GridLayoutManager(this, 2)
        monthsRecyclerView.layoutManager = layoutManager

        // 월 데이터 생성 (1월부터 12월까지) - 영어 약어 사용
        val months = mutableListOf<Month>()
        val monthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )

        for (i in 1..12) {
            months.add(Month(i, monthNames[i - 1], currentYear))
        }

        monthAdapter = MonthAdapter(months, this)
        monthsRecyclerView.adapter = monthAdapter
    }

    private fun updateYearDisplay() {
        yearTextView.text = "${currentYear}"
    }

    // MonthAdapter.OnMonthClickListener 인터페이스 구현
    override fun onMonthClick(month: Month) {
        try {
            Log.d("MainActivity", "Opening diary list for ${month.name} ${month.year}")
            val intent = Intent(this, DiaryListActivity::class.java)
            intent.putExtra("YEAR", month.year)
            intent.putExtra("MONTH", month.number)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening diary list: ${e.message}", e)
            Toast.makeText(this, "Error opening diary list", Toast.LENGTH_SHORT).show()
        }
    }

    // 년도 변경 메서드 (나중에 버튼 등으로 호출 가능)
    private fun changeYear(newYear: Int) {
        currentYear = newYear
        updateYearDisplay()
        setupRecyclerView() // RecyclerView 갱신
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                // Already on home, just close drawer
                drawerLayout.closeDrawer(Gravity.LEFT)
                return true
            }
            R.id.nav_about -> {
                // Show about dialog
                showAboutDialog()
                return true
            }
            R.id.nav_logout -> {
                // Add confirmation dialog for logout
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes") { _, _ ->
                        performLogout()
                    }
                    .setNegativeButton("No", null)
                    .show()
                return true
            }
        }
        drawerLayout.closeDrawer(Gravity.LEFT)
        return true
    }
    
    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About Five Minute Diary")
            .setMessage("A beautiful and modern diary app to capture your precious moments.\n\nFeatures:\n• Monthly diary organization\n• AI-powered insights\n• Image attachments\n• Beautiful seasonal themes\n\nVersion 1.0")
            .setPositiveButton("OK", null)
            .show()
        drawerLayout.closeDrawer(Gravity.LEFT)
    }
    
    private fun performLogout() {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, com.example.term_project.auth.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during logout: ${e.message}", e)
            Toast.makeText(this, "Error during logout", Toast.LENGTH_SHORT).show()
        }
    }
}

// 월 데이터 클래스
data class Month(
    val number: Int,    // 월 번호 (1-12)
    val name: String,   // 월 이름 ("1월", "2월" 등)
    val year: Int       // 년도
)