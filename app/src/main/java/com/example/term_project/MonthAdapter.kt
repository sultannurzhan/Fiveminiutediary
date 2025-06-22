package com.example.term_project

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.*

class MonthAdapter(
    private val months: List<Month>,
    private val listener: OnMonthClickListener
) : RecyclerView.Adapter<MonthAdapter.MonthViewHolder>() {

    class MonthViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthCard: MaterialCardView = itemView as MaterialCardView
        val monthNameText: TextView = itemView.findViewById(R.id.monthNameText)
    }

    interface OnMonthClickListener {
        fun onMonthClick(month: Month)
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

            // Populate the mini calendar with Android Calendar events
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

            // Get calendar events for this month from Android Calendar app
            val calendarEvents = getCalendarEventsForMonth(context, month.year, month.number)

            // Add empty cells for days before the first day of the month
            for (i in 0 until firstDayOfWeek) {
                addEmptyDayCell(miniCalendarGrid, context)
            }

            // Add cells for each day of the month
            for (day in 1..daysInMonth) {
                addDayCell(miniCalendarGrid, day, context, month, calendarEvents)
            }

        } catch (e: Exception) {
            Log.e("MonthAdapter", "Error populating mini calendar: ${e.message}")
        }
    }

    private fun getCalendarEventsForMonth(context: Context, year: Int, month: Int): Set<Int> {
        val eventDays = mutableSetOf<Int>()

        // Check if we have calendar permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("MonthAdapter", "Calendar permission not granted")
            return eventDays
        }

        try {
            // Calculate start and end time for the month
            val startCalendar = Calendar.getInstance()
            startCalendar.set(year, month - 1, 1, 0, 0, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
            val startTime = startCalendar.timeInMillis

            val endCalendar = Calendar.getInstance()
            endCalendar.set(year, month - 1, 1)
            endCalendar.add(Calendar.MONTH, 1)
            endCalendar.add(Calendar.MILLISECOND, -1)
            val endTime = endCalendar.timeInMillis

            // Query calendar events
            val projection = arrayOf(
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.ALL_DAY
            )

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use { c ->
                val startIndex = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val titleIndex = c.getColumnIndex(CalendarContract.Events.TITLE)
                val allDayIndex = c.getColumnIndex(CalendarContract.Events.ALL_DAY)

                while (c.moveToNext()) {
                    val eventStartTime = c.getLong(startIndex)
                    val eventTitle = c.getString(titleIndex) ?: ""
                    val isAllDay = c.getInt(allDayIndex) == 1

                    // Convert event start time to day of month
                    val eventCalendar = Calendar.getInstance()
                    eventCalendar.timeInMillis = eventStartTime

                    // Make sure the event is in the correct month/year
                    if (eventCalendar.get(Calendar.YEAR) == year &&
                        eventCalendar.get(Calendar.MONTH) + 1 == month) {
                        val dayOfMonth = eventCalendar.get(Calendar.DAY_OF_MONTH)
                        eventDays.add(dayOfMonth)

                        Log.d("MonthAdapter", "Found event '$eventTitle' on day $dayOfMonth")
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e("MonthAdapter", "Security exception reading calendar: ${e.message}")
        } catch (e: Exception) {
            Log.e("MonthAdapter", "Error reading calendar events: ${e.message}")
        }

        return eventDays
    }

    private fun addEmptyDayCell(gridLayout: GridLayout?, context: Context) {
        if (gridLayout == null) return

        val textView = LayoutInflater.from(context).inflate(R.layout.day_cell, gridLayout, false) as TextView
        textView.text = ""

        gridLayout.addView(textView)
    }

    private fun addDayCell(gridLayout: GridLayout?, day: Int, context: Context, month: Month, calendarEvents: Set<Int>) {
        if (gridLayout == null) return

        val textView = LayoutInflater.from(context).inflate(R.layout.day_cell, gridLayout, false) as TextView

        textView.text = day.toString()

        // Highlight today if it's the current month
        val today = Calendar.getInstance()
        val isToday = today.get(Calendar.DAY_OF_MONTH) == day &&
                today.get(Calendar.MONTH) + 1 == month.number &&
                today.get(Calendar.YEAR) == month.year

        // Check if this day has calendar events
        val hasEvents = calendarEvents.contains(day)

        when {
            isToday && hasEvents -> {
                // Today with events - special highlighting
                textView.setBackgroundResource(R.drawable.circle_background_today_with_events)
                textView.alpha = 1.0f
            }
            isToday -> {
                // Today without events - normal today highlighting
                textView.setBackgroundResource(R.drawable.circle_background)
                textView.alpha = 1.0f
            }
            hasEvents -> {
                // Day with events - event indicator
                textView.setBackgroundResource(R.drawable.circle_background_events)
                textView.alpha = 0.8f
            }
            else -> {
                // Normal day
                textView.background = null
                textView.alpha = 0.7f
            }
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