package dev.lazier.pub.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Everything the app remembers between launches.
 *
 * SharedPreferences is enough for a handful of values and needs no dependency.
 * Compose observes the mutable state, so a change in Settings redraws the app.
 */
object Settings {

    private const val FILE = "lazierpub"
    private lateinit var prefs: SharedPreferences

    var serverUrl by mutableStateOf("http://10.0.2.2:8787")
        private set
    var token by mutableStateOf("")
        private set
    var theme by mutableStateOf("dark")
        private set
    var columns by mutableIntStateOf(3)
        private set
    /** PDFs are listed only when this is on; EPUB is the product. */
    var showPdf by mutableStateOf(false)
        private set
    /** Android 12+ wallpaper palette. Off by default so the brand stays put. */
    var dynamicColor by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        serverUrl = prefs.getString("serverUrl", serverUrl) ?: serverUrl
        token = prefs.getString("token", "") ?: ""
        theme = prefs.getString("theme", "dark") ?: "dark"
        columns = prefs.getInt("columns", 3)
        showPdf = prefs.getBoolean("showPdf", false)
        dynamicColor = prefs.getBoolean("dynamicColor", false)
    }

    fun updateServer(url: String, token: String) {
        serverUrl = url.trim().trimEnd('/')
        this.token = token.trim()
        prefs.edit().putString("serverUrl", serverUrl).putString("token", this.token).apply()
    }

    fun updateTheme(value: String) {
        theme = value
        prefs.edit().putString("theme", value).apply()
    }

    fun updateColumns(value: Int) {
        columns = value.coerceIn(2, 5)
        prefs.edit().putInt("columns", columns).apply()
    }

    fun updateShowPdf(value: Boolean) {
        showPdf = value
        prefs.edit().putBoolean("showPdf", value).apply()
    }

    fun updateDynamicColor(value: Boolean) {
        dynamicColor = value
        prefs.edit().putBoolean("dynamicColor", value).apply()
    }

    val configured: Boolean get() = serverUrl.isNotBlank()
}
