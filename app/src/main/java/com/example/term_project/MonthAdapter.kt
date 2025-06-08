package com.example.term_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class MonthAdapter(
    private val months: List<Month>,
    private val listener: OnMonthClickListener
) : RecyclerView.Adapter<MonthAdapter.MonthViewHolder>() {

    interface OnMonthClickListener {
        fun onMonthClick(month: Month)
    }

    class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthCard: CardView = itemView.findViewById(R.id.monthCard)
        val monthText: TextView = itemView.findViewById(R.id.monthText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_month, parent, false)
        return MonthViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        val month = months[position]

        holder.monthText.text = month.name

        // 카드 클릭 리스너 설정
        holder.monthCard.setOnClickListener {
            listener.onMonthClick(month)
        }

        // 카드 클릭 효과를 위한 ripple 효과 활성화
        holder.monthCard.isClickable = true
        holder.monthCard.isFocusable = true
    }

    override fun getItemCount(): Int = months.size
}