package com.example.term_project

import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.*

class MonthAdapter(
    private val months: List<Month>,
    private val listener: OnMonthClickListener
) : RecyclerView.Adapter<MonthAdapter.MonthViewHolder>() {

    interface OnMonthClickListener {
        fun onMonthClick(month: Month)
    }

    class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthCard: MaterialCardView = itemView as MaterialCardView
        val monthNameText: TextView = itemView.findViewById(R.id.monthNameText)
        val weekDaysHeader: LinearLayout? = itemView.findViewById(R.id.weekDaysHeader)
        val miniCalendarGrid: GridLayout? = itemView.findViewById(R.id.miniCalendarGrid)
        val entryIndicator: View? = itemView.findViewById(R.id.entryIndicator)
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
            holder.monthNameText.text = month.name
            
            // Apply seasonal theme
            setSeasonalBackground(holder, month.number)
            
            // Populate the mini calendar
            populateMiniCalendar(holder, month, context)

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

    private fun populateMiniCalendar(holder: MonthViewHolder, month: Month, context: Context) {
        try {
            val miniCalendarGrid = holder.itemView.findViewById<GridLayout>(R.id.miniCalendarGrid)
            miniCalendarGrid?.removeAllViews()
            
            // Calculate calendar for the current year and month
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, month.year)
            calendar.set(Calendar.MONTH, month.number - 1) // Calendar.MONTH is 0-based
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Make it 0-based (Sunday = 0)
            
            // Add empty cells for days before the first day of the month
            for (i in 0 until firstDayOfWeek) {
                addEmptyDayCell(miniCalendarGrid, context)
            }
            
            // Add cells for each day of the month
            for (day in 1..daysInMonth) {
                addDayCell(miniCalendarGrid, day, context, month)
            }
            
        } catch (e: Exception) {
            Log.e("MonthAdapter", "Error populating mini calendar: ${e.message}")
        }
    }

    private fun addEmptyDayCell(gridLayout: GridLayout?, context: Context) {
        if (gridLayout == null) return
        
        val textView = LayoutInflater.from(context).inflate(R.layout.day_cell, gridLayout, false) as TextView
        textView.text = ""
        
        gridLayout.addView(textView)
    }

    private fun addDayCell(gridLayout: GridLayout?, day: Int, context: Context, month: Month) {
        if (gridLayout == null) return
        
        val textView = LayoutInflater.from(context).inflate(R.layout.day_cell, gridLayout, false) as TextView
        
        textView.text = day.toString()
        
        // Highlight today if it's the current month
        val today = Calendar.getInstance()
        if (today.get(Calendar.DAY_OF_MONTH) == day && 
            today.get(Calendar.MONTH) + 1 == month.number &&
            today.get(Calendar.YEAR) == month.year) {
            textView.setBackgroundResource(R.drawable.circle_background)
            textView.alpha = 1.0f
        }
        
        gridLayout.addView(textView)
    }

    private fun setSeasonalBackground(holder: MonthViewHolder, month: Int) {
        val backgroundRes = when(month) {
            3, 4, 5 -> R.drawable.gradient_spring    // Spring: Mar, Apr, May
            6, 7, 8 -> R.drawable.gradient_summer    // Summer: Jun, Jul, Aug  
            9, 10, 11 -> R.drawable.gradient_autumn  // Autumn: Sep, Oct, Nov
            12, 1, 2 -> R.drawable.gradient_winter   // Winter: Dec, Jan, Feb
            else -> R.drawable.gradient_primary      // Fallback
        }
        
        // Set background on the card's child RelativeLayout
        val relativeLayout = holder.monthCard.getChildAt(0)
        relativeLayout?.setBackgroundResource(backgroundRes)
    }

    override fun getItemCount(): Int = months.size
}