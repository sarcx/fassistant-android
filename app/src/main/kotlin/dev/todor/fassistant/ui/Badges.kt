package dev.todor.fassistant.ui

import android.content.Context
import dev.todor.fassistant.Badge
import dev.todor.fassistant.R

internal fun Context.badgeColor(badge: Badge): Int = getColor(
    when (badge) {
        Badge.EXACT, Badge.STRONG -> R.color.badge_good
        Badge.WEAK -> R.color.badge_warn
        Badge.NO_LAUNCHER -> R.color.badge_bad
    }
)
