package dev.lazier.pub.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A full-height display sheet, the way a reader app does it: every control is
 * visible at once, and a change reaches the page when the finger lifts.
 */
@Composable
fun ReaderDisplayDialog(
    config: ReaderConfig,
    onChange: (ReaderConfig) -> Unit,
    onDone: () -> Unit,
) {
    var draft by remember(config) { mutableStateOf(config) }

    fun push(next: ReaderConfig) {
        draft = next
        onChange(next)
    }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Display",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { push(ReaderConfig()) }) { Text("Reset") }
                    TextButton(onClick = onDone) { Text("Done") }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    SectionTitle("Theme")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ThemeSwatch("Light", Color(0xFFFFFFFF), Color(0xFF17181C), draft.theme == "light") {
                            push(draft.withTheme("light"))
                        }
                        ThemeSwatch("Sepia", Color(0xFFF6ECD9), Color(0xFF4A3B2A), draft.theme == "sepia") {
                            push(draft.withTheme("sepia"))
                        }
                        ThemeSwatch("Gray", Color(0xFF2B2D31), Color(0xFFD8DADF), draft.theme == "gray") {
                            push(draft.withTheme("gray"))
                        }
                        ThemeSwatch("Dark", Color(0xFF101216), Color(0xFFE3E5EA), draft.theme == "dark") {
                            push(draft.withTheme("dark"))
                        }
                        ThemeSwatch("Black", Color(0xFF000000), Color(0xFFC8C8C8), draft.theme == "black") {
                            push(draft.withTheme("black"))
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Background",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            0xFFFFFFFF.toInt(), 0xFFF6ECD9.toInt(), 0xFFE9E5DA.toInt(),
                            0xFF2B2D31.toInt(), 0xFF1E2A22.toInt(), 0xFF101216.toInt(),
                            0xFF000000.toInt(), 0xFF0F1B2A.toInt(),
                        ).forEach { value ->
                            val color = Color(value)
                            ColorDot(
                                color = color,
                                selected = draft.theme == "custom" && draft.backgroundColor == value,
                                onClick = {
                                    push(draft.copy(theme = "custom", backgroundColor = value))
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Text colour",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            0xFF17181C.toInt(), 0xFF4A3B2A.toInt(), 0xFF5B616B.toInt(),
                            0xFF9AA0AA.toInt(), 0xFFE3E5EA.toInt(), 0xFFC8C8C8.toInt(), 0xFFFFFFFF.toInt(),
                        ).forEach { value ->
                            val color = Color(value)
                            ColorDot(
                                color = color,
                                selected = draft.theme == "custom" && draft.textColor == value,
                                onClick = {
                                    push(draft.copy(theme = "custom", textColor = value))
                                },
                            )
                        }
                    }

                    SectionTitle("Font")
                    ChipRow(
                        options = listOf(
                            "publisher" to "Publisher",
                            "serif" to "Serif",
                            "sans" to "Sans",
                            "mono" to "Mono",
                            "dyslexic" to "OpenDyslexic",
                        ),
                        selected = draft.fontFamily,
                        onSelect = { push(draft.copy(fontFamily = it)) },
                    )

                    Spacer(Modifier.height(6.dp))
                    SliderRow(
                        label = "Text size",
                        valueLabel = "${(draft.fontSize * 100).roundToInt()}%",
                        value = draft.fontSize,
                        range = 0.7f..2.6f,
                        onValueChange = { draft = draft.copy(fontSize = it) },
                        onFinished = { onChange(draft) },
                    )
                    SliderRow(
                        label = "Line spacing",
                        valueLabel = String.format(Locale.US, "%.2f×", draft.lineHeight),
                        value = draft.lineHeight,
                        range = 0.8f..2.4f,
                        onValueChange = { draft = draft.copy(lineHeight = it) },
                        onFinished = { onChange(draft) },
                    )

                    SectionTitle("Layout")
                    Text(
                        text = "Alignment",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        options = listOf(
                            "publisher" to "Publisher",
                            "justify" to "Justify",
                            "left" to "Left",
                        ),
                        selected = draft.textAlign,
                        onSelect = { push(draft.copy(textAlign = it)) },
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Columns",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        options = listOf("auto" to "Auto", "one" to "One", "two" to "Two"),
                        selected = draft.columns,
                        onSelect = { push(draft.copy(columns = it)) },
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Reading mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        options = listOf("paged" to "Paged", "scroll" to "Scroll"),
                        selected = if (draft.scroll) "scroll" else "paged",
                        onSelect = { push(draft.copy(scroll = it == "scroll")) },
                    )

                    Spacer(Modifier.height(12.dp))
                    SliderRow(
                        label = "Side margins",
                        valueLabel = "${(draft.margins * 100).roundToInt()}%",
                        value = draft.margins,
                        range = 0f..1f,
                        onValueChange = { draft = draft.copy(margins = it) },
                        onFinished = { onChange(draft) },
                    )
                    SliderRow(
                        label = "Top & bottom margins",
                        valueLabel = "${(draft.verticalMargins * 100).roundToInt()}%",
                        value = draft.verticalMargins,
                        range = 0f..1f,
                        onValueChange = { draft = draft.copy(verticalMargins = it) },
                        onFinished = { onChange(draft) },
                    )
                    SliderRow(
                        label = "Paragraph spacing",
                        valueLabel = "${(draft.paragraphSpacing * 100).roundToInt()}%",
                        value = draft.paragraphSpacing,
                        range = 0f..1.5f,
                        onValueChange = { draft = draft.copy(paragraphSpacing = it) },
                        onFinished = { onChange(draft) },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Publisher styles", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Let the book's own CSS win",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.publisherStyles,
                            onCheckedChange = { push(draft.copy(publisherStyles = it)) },
                        )
                    }

                    SectionTitle("Images")
                    ChipRow(
                        options = listOf("none" to "Original", "darken" to "Dim", "invert" to "Invert"),
                        selected = draft.imageFilter,
                        onSelect = { push(draft.copy(imageFilter = it)) },
                    )
                    Text(
                        text = "Dim lowers image brightness in dark themes. Invert flips light artwork for night reading.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 10.dp))
}

@Composable
private fun ThemeSwatch(
    label: String,
    background: Color,
    foreground: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        onValueChangeFinished = onFinished,
    )
}
