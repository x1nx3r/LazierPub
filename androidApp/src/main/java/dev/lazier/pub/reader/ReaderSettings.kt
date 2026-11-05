package dev.lazier.pub.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ImageFilter
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.Color as ReadiumColor

/**
 * Everything the reading page can change.
 *
 * The values are stored one by one in SharedPreferences. A reader has few
 * settings and they must be correct on the first page, before any coroutine
 * has run, so a plain file read at startup beats a serializer.
 */
data class ReaderConfig(
    val theme: String = "dark",
    val backgroundColor: Int = 0xFF101216.toInt(),
    val textColor: Int = 0xFFE3E5EA.toInt(),
    val fontFamily: String = "publisher",
    val fontSize: Float = 1.0f,
    val lineHeight: Float = 1.5f,
    /** Side margins, 0..1. The reader insets the page, not the text CSS. */
    val margins: Float = 0.4f,
    /** Extra space above and below the page, on top of the reserved bars. */
    val verticalMargins: Float = 0.0f,
    val paragraphSpacing: Float = 0.0f,
    val textAlign: String = "publisher",
    val columns: String = "auto",
    val scroll: Boolean = false,
    val publisherStyles: Boolean = false,
    val imageFilter: String = "darken",
) {
    val dark: Boolean get() = Color(backgroundColor).luminance() < 0.5f

    val background: Color get() = Color(backgroundColor)

    val foreground: Color get() = Color(textColor)
}

fun ReaderConfig.withTheme(key: String): ReaderConfig = when (key) {
    "light" -> copy(theme = "light", backgroundColor = 0xFFFFFFFF.toInt(), textColor = 0xFF17181C.toInt())
    "sepia" -> copy(theme = "sepia", backgroundColor = 0xFFF6ECD9.toInt(), textColor = 0xFF4A3B2A.toInt())
    "gray" -> copy(theme = "gray", backgroundColor = 0xFF2B2D31.toInt(), textColor = 0xFFD8DADF.toInt())
    "black" -> copy(theme = "black", backgroundColor = 0xFF000000.toInt(), textColor = 0xFFC8C8C8.toInt())
    "dark" -> copy(theme = "dark", backgroundColor = 0xFF101216.toInt(), textColor = 0xFFE3E5EA.toInt())
    else -> copy(theme = "custom")
}

object ReaderSettings {

    private const val FILE = "reader"

    private lateinit var prefs: SharedPreferences

    var config by mutableStateOf(ReaderConfig())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = ReaderConfig()
        config = ReaderConfig(
            theme = prefs.getString("theme", defaults.theme) ?: defaults.theme,
            backgroundColor = prefs.getInt("backgroundColor", defaults.backgroundColor),
            textColor = prefs.getInt("textColor", defaults.textColor),
            fontFamily = prefs.getString("fontFamily", defaults.fontFamily) ?: defaults.fontFamily,
            fontSize = prefs.getFloat("fontSize", defaults.fontSize),
            lineHeight = prefs.getFloat("lineHeight", defaults.lineHeight),
            margins = prefs.getFloat("margins", defaults.margins),
            verticalMargins = prefs.getFloat("verticalMargins", defaults.verticalMargins),
            paragraphSpacing = prefs.getFloat("paragraphSpacing", defaults.paragraphSpacing),
            textAlign = prefs.getString("textAlign", defaults.textAlign) ?: defaults.textAlign,
            columns = prefs.getString("columns", defaults.columns) ?: defaults.columns,
            scroll = prefs.getBoolean("scroll", defaults.scroll),
            publisherStyles = prefs.getBoolean("publisherStyles", defaults.publisherStyles),
            imageFilter = prefs.getString("imageFilter", defaults.imageFilter) ?: defaults.imageFilter,
        )
    }

    fun save(value: ReaderConfig) {
        config = value
        prefs.edit()
            .putString("theme", value.theme)
            .putInt("backgroundColor", value.backgroundColor)
            .putInt("textColor", value.textColor)
            .putString("fontFamily", value.fontFamily)
            .putFloat("fontSize", value.fontSize)
            .putFloat("lineHeight", value.lineHeight)
            .putFloat("margins", value.margins)
            .putFloat("verticalMargins", value.verticalMargins)
            .putFloat("paragraphSpacing", value.paragraphSpacing)
            .putString("textAlign", value.textAlign)
            .putString("columns", value.columns)
            .putBoolean("scroll", value.scroll)
            .putBoolean("publisherStyles", value.publisherStyles)
            .putString("imageFilter", value.imageFilter)
            .apply()
    }

    fun reset() = save(ReaderConfig())

    /** Maps the stored choices onto Readium's preferences. */
    fun preferences(value: ReaderConfig = config): EpubPreferences {
        // A custom palette still needs a base theme: Readium resolves the
        // image filter and link colours from it. Explicit colours win.
        val custom = value.theme != "light" && value.theme != "sepia" && value.theme != "dark"
        val preset = when (value.theme) {
            "light" -> Theme.LIGHT
            "sepia" -> Theme.SEPIA
            "dark" -> Theme.DARK
            else -> if (value.dark) Theme.DARK else Theme.LIGHT
        }

        return EpubPreferences(
            backgroundColor = if (custom) ReadiumColor(value.backgroundColor) else null,
            textColor = if (custom) ReadiumColor(value.textColor) else null,
            columnCount = when (value.columns) {
                "one" -> ColumnCount.ONE
                "two" -> ColumnCount.TWO
                else -> ColumnCount.AUTO
            },
            fontFamily = when (value.fontFamily) {
                "serif" -> FontFamily.SERIF
                "sans" -> FontFamily.SANS_SERIF
                "mono" -> FontFamily.MONOSPACE
                "dyslexic" -> FontFamily.OPEN_DYSLEXIC
                else -> null
            },
            fontSize = value.fontSize.toDouble(),
            lineHeight = value.lineHeight.toDouble(),
            paragraphSpacing = value.paragraphSpacing.toDouble(),
            publisherStyles = value.publisherStyles,
            scroll = value.scroll,
            textAlign = when (value.textAlign) {
                "justify" -> TextAlign.JUSTIFY
                "left" -> TextAlign.START
                else -> null
            },
            theme = preset,
            imageFilter = when (value.imageFilter) {
                "none" -> null
                "invert" -> ImageFilter.INVERT
                else -> ImageFilter.DARKEN
            },
        )
    }
}

/** The chrome follows the page colours, so the bars never glare in the dark. */
@Composable
fun ReaderTheme(config: ReaderConfig, content: @Composable () -> Unit) {
    val background = config.background
    val foreground = config.foreground
    val dark = config.dark
    val accent = if (dark) Color(0xFF8AB4F8) else Color(0xFF2F5DA8)

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF0A1B33),
            background = background,
            onBackground = foreground,
            surface = background,
            onSurface = foreground,
            surfaceVariant = foreground.copy(alpha = 0.12f),
            onSurfaceVariant = foreground.copy(alpha = 0.62f),
            outline = foreground.copy(alpha = 0.25f),
            error = Color(0xFFFFB4AB),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = background,
            onBackground = foreground,
            surface = background,
            onSurface = foreground,
            surfaceVariant = foreground.copy(alpha = 0.08f),
            onSurfaceVariant = foreground.copy(alpha = 0.62f),
            outline = foreground.copy(alpha = 0.22f),
            error = Color(0xFFB3261E),
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
