package dev.lazier.pub.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun relativeTime(millis: Long?): String {
    if (millis == null || millis <= 0) return ""
    val difference = System.currentTimeMillis() - millis
    return when {
        difference < 60_000 -> "just now"
        difference < 3_600_000 -> "${difference / 60_000} min ago"
        difference < 86_400_000 -> "${difference / 3_600_000} h ago"
        difference < 30L * 86_400_000 -> "${difference / 86_400_000} d ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}

fun percentText(fraction: Double?): String {
    val value = (fraction ?: 0.0).coerceIn(0.0, 1.0)
    return when {
        value <= 0.0 -> "0%"
        value < 0.01 -> "<1%"
        value >= 0.9995 -> "100%"
        else -> "${(value * 100).roundToInt()}%"
    }
}
