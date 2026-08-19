package dev.todor.fassistant.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.todor.fassistant.R

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

internal fun Context.verticalLayout(padding: Int = 16): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val space = dp(padding)
    setPadding(space, space, space, space)
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.horizontalLayout(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.scrolling(content: View): ScrollView = ScrollView(this).apply {
    addView(content)
    isFillViewport = true
}

internal fun Context.textView(text: CharSequence, appearance: Int): TextView = TextView(this).apply {
    setTextAppearance(appearance)
    this.text = text
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.title(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault_Large).apply {
        setTypeface(typeface, Typeface.BOLD)
    }

internal fun Context.body(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault)

internal fun Context.caption(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault_Small)

internal fun Context.heading(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault_Small).apply {
        setTypeface(typeface, Typeface.BOLD)
        isAllCaps = true
        letterSpacing = 0.09f
        setPadding(0, dp(20), 0, dp(4))
    }

internal fun Context.button(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.badge(text: CharSequence, color: Int): TextView = TextView(this).apply {
    this.text = text
    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
    setTextColor(color)
    isAllCaps = true
    setPadding(dp(8), dp(2), dp(8), dp(2))
    background = GradientDrawable().apply {
        cornerRadius = dp(4).toFloat()
        setColor((color and 0x00FFFFFF) or 0x22000000)
        setStroke(dp(1), color)
    }
}

internal fun Context.divider(): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
        topMargin = dp(8)
        bottomMargin = dp(8)
    }
    setBackgroundColor(0x22808080)
}

internal fun Context.spacer(height: Int): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
}

internal fun Context.formatDuration(ms: Long): String = when {
    ms < 60_000L -> getString(R.string.duration_seconds, (ms / 1_000L).toInt())
    ms < 3_600_000L -> getString(R.string.duration_minutes, (ms / 60_000L).toInt())
    ms < 86_400_000L -> getString(R.string.duration_hours, (ms / 3_600_000L).toInt())
    else -> getString(R.string.duration_days, (ms / 86_400_000L).toInt())
}

internal fun Context.formatAgo(then: Long): String {
    if (then <= 0L) return getString(R.string.main_never)
    val delta = System.currentTimeMillis() - then
    if (delta < 10_000L) return getString(R.string.duration_just_now)
    return getString(R.string.duration_ago, formatDuration(delta))
}
