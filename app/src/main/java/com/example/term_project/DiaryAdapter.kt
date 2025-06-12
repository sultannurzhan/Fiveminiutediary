package com.example.term_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class DiaryAdapter(
    private val diaryEntries: MutableList<DiaryEntry>,
    private val listener: OnDiaryClickListener,

) : RecyclerView.Adapter<DiaryAdapter.DiaryViewHolder>() {

    interface OnDiaryClickListener {
        fun onDiaryClick(diary: DiaryEntry)
    }

    class DiaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val diaryCard: CardView = itemView.findViewById(R.id.diaryCard)
        val titleText: TextView = itemView.findViewById(R.id.titleText)
        val contentPreview: TextView = itemView.findViewById(R.id.contentPreview)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diary, parent, false)
        return DiaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        val diary = diaryEntries[position]

        holder.titleText.text = diary.title
        holder.contentPreview.text = getContentPreview(diary.body)
        holder.dateText.text = formatDate(diary.date)

        // 카드 클릭 리스너 설정
        holder.diaryCard.setOnClickListener {
            listener.onDiaryClick(diary)
        }

        // 카드 클릭 효과 활성화
        holder.diaryCard.isClickable = true
        holder.diaryCard.isFocusable = true
    }

    override fun getItemCount(): Int = diaryEntries.size

    fun updateEntries(newEntries: List<DiaryEntry>) {
        diaryEntries.clear()
        diaryEntries.addAll(newEntries)
        notifyDataSetChanged()
    }

    private fun getContentPreview(content: String): String {
        return if (content.length > 50) {
            content.substring(0, 50) + "..."
        } else {
            content
        }
    }



    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }
}