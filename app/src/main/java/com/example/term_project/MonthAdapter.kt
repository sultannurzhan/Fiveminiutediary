package com.example.term_project

import android.animation.ObjectAnimator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class MonthAdapter(
    private val months: List<Month>,
    private val listener: OnMonthClickListener
) : RecyclerView.Adapter<MonthAdapter.MonthViewHolder>() {

    interface OnMonthClickListener {
        fun onMonthClick(month: Month)
    }

    class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthCard: MaterialCardView = itemView.findViewById(R.id.monthCard)
        val monthText: TextView = itemView.findViewById(R.id.monthText)
        val monthNumber: TextView? = itemView.findViewById(R.id.monthNumber)
        val gradientOverlay: View? = itemView.findViewById(R.id.gradientOverlay)
        val seasonalPattern: View? = itemView.findViewById(R.id.seasonalPattern)
        val entryIndicator: View? = itemView.findViewById(R.id.entryIndicator)
        val entryCount: TextView? = itemView.findViewById(R.id.entryCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_month, parent, false)
        return MonthViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        try {
            val month = months[position]
            val context = holder.itemView.context

            // Set month name
            holder.monthText.text = month.name
            
            // Set month number if the view exists
            holder.monthNumber?.text = String.format("%02d", month.number)

            // Set seasonal colors based on month
            val seasonalColor = when (month.number) {
                3, 4, 5 -> ContextCompat.getColor(context, R.color.month_spring) // Spring
                6, 7, 8 -> ContextCompat.getColor(context, R.color.month_summer) // Summer
                9, 10, 11 -> ContextCompat.getColor(context, R.color.month_autumn) // Autumn
                else -> ContextCompat.getColor(context, R.color.month_winter) // Winter
            }

            // Apply seasonal theme (only if views exist)
            holder.seasonalPattern?.setBackgroundColor(seasonalColor)
            holder.gradientOverlay?.alpha = 0.15f

            // Card click animation and listener
            holder.monthCard.setOnClickListener {
                try {
                    // Scale animation on click
                    val scaleDown = ObjectAnimator.ofFloat(holder.monthCard, "scaleX", 1.0f, 0.95f)
                    val scaleUp = ObjectAnimator.ofFloat(holder.monthCard, "scaleX", 0.95f, 1.0f)
                    scaleDown.duration = 100
                    scaleUp.duration = 100
                    
                    scaleDown.start()
                    scaleUp.start()
                    
                    Log.d("MonthAdapter", "Month clicked: ${month.name} (${month.number}/${month.year})")
                    listener.onMonthClick(month)
                } catch (e: Exception) {
                    Log.e("MonthAdapter", "Error in month click: ${e.message}", e)
                }
            }

            // Enable card interactions
            holder.monthCard.isClickable = true
            holder.monthCard.isFocusable = true
            
        } catch (e: Exception) {
            Log.e("MonthAdapter", "Error binding month at position $position: ${e.message}", e)
        }
    }

    override fun getItemCount(): Int = months.size
}