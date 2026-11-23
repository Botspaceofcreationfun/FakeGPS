package com.gpsfakepro.util

import java.util.Locale

/** Human-friendly formatters for coordinates, distances and speeds. */
object Formatters {

    /** "51.507400, -0.127800" — 6 decimals (~0.1 m). */
    fun coords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.6f, %.6f", lat, lon)

    /** "51.5074" — compact coordinate for lists. */
    fun coordShort(value: Double): String = String.format(Locale.US, "%.5f", value)

    /** Distance: "<1000 m" as metres, otherwise km. */
    fun distance(meters: Double): String = when {
        meters < 1000.0 -> String.format(Locale.US, "%.0f m", meters)
        else -> String.format(Locale.US, "%.2f km", meters / 1000.0)
    }

    /** Speed as m/s plus km/h, e.g. "5.0 · 18". */
    fun speed(mps: Double): String =
        String.format(Locale.US, "%.1f", mps)

    fun speedKmh(mps: Double): String =
        String.format(Locale.US, "%.0f", mps * 3.6)

    /** Pause countdown, e.g. "3.5 s". */
    fun seconds(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1000.0)
}
