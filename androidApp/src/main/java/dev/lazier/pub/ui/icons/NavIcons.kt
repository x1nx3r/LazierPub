package dev.lazier.pub.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A small nav icon set, drawn locally because the app depends on
 * `material-icons-core` only. Each glyph follows the Material 24dp grid.
 */
val LibraryIcon: ImageVector by lazy {
    icon("Library") {
        path(fill = SolidColor(Color.Black)) {
            // Left page of an open book.
            moveTo(11f, 5.4f)
            curveTo(9.5f, 4.8f, 7.6f, 4.5f, 5.7f, 4.5f)
            curveTo(4.4f, 4.5f, 3.1f, 4.7f, 2f, 5.2f)
            lineTo(2f, 19.1f)
            curveTo(3.1f, 18.6f, 4.4f, 18.4f, 5.7f, 18.4f)
            curveTo(7.6f, 18.4f, 9.5f, 18.7f, 11f, 19.4f)
            close()
            // Right page.
            moveTo(13f, 5.4f)
            curveTo(14.5f, 4.8f, 16.4f, 4.5f, 18.3f, 4.5f)
            curveTo(19.6f, 4.5f, 20.9f, 4.7f, 22f, 5.2f)
            lineTo(22f, 19.1f)
            curveTo(20.9f, 18.6f, 19.6f, 18.4f, 18.3f, 18.4f)
            curveTo(16.4f, 18.4f, 14.5f, 18.7f, 13f, 19.4f)
            close()
        }
    }
}

val UpdatesIcon: ImageVector by lazy {
    icon("Updates") {
        path(fill = SolidColor(Color.Black)) {
            // Large four-point star.
            moveTo(11.5f, 9.5f)
            lineTo(9f, 4f)
            lineTo(6.5f, 9.5f)
            lineTo(1f, 12f)
            lineTo(6.5f, 14.5f)
            lineTo(9f, 20f)
            lineTo(11.5f, 14.5f)
            lineTo(17f, 12f)
            close()
            // Two small stars.
            moveTo(19f, 9f)
            lineTo(20.25f, 6.25f)
            lineTo(23f, 5f)
            lineTo(20.25f, 3.75f)
            lineTo(19f, 1f)
            lineTo(17.75f, 3.75f)
            lineTo(15f, 5f)
            lineTo(17.75f, 6.25f)
            close()
            moveTo(19f, 15f)
            lineTo(17.75f, 17.75f)
            lineTo(15f, 19f)
            lineTo(17.75f, 20.25f)
            lineTo(19f, 23f)
            lineTo(20.25f, 20.25f)
            lineTo(23f, 19f)
            lineTo(20.25f, 17.75f)
            close()
        }
    }
}

val HistoryIcon: ImageVector by lazy {
    icon("History") {
        // Ring: outer circle minus inner circle.
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 4.5f)
            curveTo(7.86f, 4.5f, 4.5f, 7.86f, 4.5f, 12f)
            curveTo(4.5f, 16.14f, 7.86f, 19.5f, 12f, 19.5f)
            curveTo(16.14f, 19.5f, 19.5f, 16.14f, 19.5f, 12f)
            curveTo(19.5f, 7.86f, 16.14f, 4.5f, 12f, 4.5f)
            close()
        }
        // Hands.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 7f)
            lineTo(12f, 12.2f)
            lineTo(15.4f, 14.2f)
        }
    }
}

val BrowseIcon: ImageVector by lazy {
    icon("Browse") {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 3.5f)
            curveTo(16.69f, 3.5f, 20.5f, 7.31f, 20.5f, 12f)
            curveTo(20.5f, 16.69f, 16.69f, 20.5f, 12f, 20.5f)
            curveTo(7.31f, 20.5f, 3.5f, 16.69f, 3.5f, 12f)
            curveTo(3.5f, 7.31f, 7.31f, 3.5f, 12f, 3.5f)
            close()
        }
        // Compass needle.
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.6f, 7.4f)
            lineTo(13.4f, 13.4f)
            lineTo(7.4f, 16.6f)
            lineTo(10.6f, 10.6f)
            close()
        }
    }
}

val MoreIcon: ImageVector by lazy {
    icon("More") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 10f)
            curveTo(4.9f, 10f, 4f, 10.9f, 4f, 12f)
            curveTo(4f, 13.1f, 4.9f, 14f, 6f, 14f)
            curveTo(7.1f, 14f, 8f, 13.1f, 8f, 12f)
            curveTo(8f, 10.9f, 7.1f, 10f, 6f, 10f)
            close()
            moveTo(18f, 10f)
            curveTo(16.9f, 10f, 16f, 10.9f, 16f, 12f)
            curveTo(16f, 13.1f, 16.9f, 14f, 18f, 14f)
            curveTo(19.1f, 14f, 20f, 13.1f, 20f, 12f)
            curveTo(20f, 10.9f, 19.1f, 10f, 18f, 10f)
            close()
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
            curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
            curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
            curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
            close()
        }
    }
}

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()
