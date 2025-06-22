package com.example.term_project

import android.app.Application
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MyApplication : Application() {
    lateinit var titleService: DiaryTitleService
        private set

    override fun onCreate() {
        super.onCreate()
        titleService = DiaryTitleService(this)


        GlobalScope.launch {
            titleService.initializeIfNeeded()
        }
    }
}