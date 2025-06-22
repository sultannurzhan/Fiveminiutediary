package com.example.term_project

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

data class DiaryEntry(
    var id: String = "",
    val title: String = "",
    val body: String = "",
    val user: String = "",
    val created_at: Timestamp? = null,
    val updated_at: Timestamp? = null,
    val llm_used: Boolean = false,
    val month: String = "", // Format: "YYYY-MM" (e.g., "2025-06")
    val images: List<String> = emptyList() // List of image file paths stored locally
) {
    val date: String
        get() = created_at?.let { timestamp ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(timestamp.toDate())
        } ?: ""
}
