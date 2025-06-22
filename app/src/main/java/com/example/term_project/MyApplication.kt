package com.example.term_project

import android.app.Application
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MyApplication : Application() {
    lateinit var titleService: DiaryTitleService
        private set

    override fun onCreate() {
        super.onCreate()
        titleService = DiaryTitleService(this)

        // Initialize the title service in background with error handling
        GlobalScope.launch {
            try {
                titleService.initializeIfNeeded()
                Log.d("MyApplication", "Title service initialized successfully")
            } catch (e: Exception) {
                Log.e("MyApplication", "Failed to initialize title service: ${e.message}", e)
                // Continue app execution even if title service fails to initialize
            }
        }
    }
}