package com.example.manualreply

import android.graphics.Color

object AvatarUtil {

    // Telegram-style palette — a handful of saturated, friendly colors
    private val palette = listOf(
        "#EE5253", // red
        "#F97F51", // orange
        "#B33771", // pink
        "#6D214F", // plum
        "#3B3B98", // indigo
        "#1B9CFC", // blue
        "#0FB9B1", // teal
        "#58B19F", // green
        "#CAD3C8".let { "#8E44AD" } // purple (kept distinct)
    )

    // Same username always maps to the same color, so it feels consistent
    // across the app instead of changing on every refresh.
    fun colorFor(seed: String): Int {
        if (seed.isEmpty()) return Color.parseColor(palette[0])
        val index = Math.abs(seed.hashCode()) % palette.size
        return Color.parseColor(palette[index])
    }

    fun initialFor(username: String): String {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return "?"
        return trimmed.first().uppercaseChar().toString()
    }
}
