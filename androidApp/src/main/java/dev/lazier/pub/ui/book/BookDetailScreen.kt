package dev.lazier.pub.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.Api
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.reader.ReaderActivity
import dev.lazier.pub.reader.ReaderFiles
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.shared.Shelf
import dev.lazier.pub.ui.CoverImage
import dev.lazier.pub.ui.MessageBanner
import dev.lazier.pub.ui.SectionTitle
import dev.lazier.pub.ui.Tag
import dev.lazier.pub.ui.formatBytes
import dev.lazier.pub.ui.percentText
import dev.lazier.pub.ui.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(book: Book, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reading = LibraryStore.progressFor(book.id)

    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var failure by remember { mutableStateOf<String?>(null) }
    var shelvesOpen by remember { mutableStateOf(false) }

    val readable = book.kind == BookKind.EPUB

    fun read() {
        scope.launch {
            downloading = true
            downloadProgress = 0f
            failure = null
            try {
                val file = ReaderFiles.prepare(context, book.id) { downloadProgress = it }
                context.startActivity(ReaderActivity.intent(context, book, file, reading))
            } catch (error: Exception) {
                failure = error.message ?: "Could not download the book."
            } finally {
                downloading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ),
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(
                        bookId = book.id.takeIf { book.hasCover },
                        width = 400,
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp)
                            .shadow(6.dp, RoundedCornerShape(12.dp)),
                        corner = 12.dp,
                        label = book.title,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = book.author ?: "Unknown author",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(book.kind.name)
                            book.volume?.let { Tag("Vol. $it") }
                            book.language?.let { Tag(it.uppercase()) }
                        }
                        book.series?.takeIf { it.isNotBlank() }?.let { series ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = series,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { read() },
                        enabled = readable && !downloading,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                !readable -> "Not readable yet"
                                downloading -> "Downloading ${(downloadProgress * 100).toInt()}%"
                                reading != null -> "Continue · ${percentText(reading.percent)}"
                                else -> "Start reading"
                            },
                        )
                    }
                    FilledTonalButton(
                        onClick = { shelvesOpen = true },
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shelf")
                    }
                }

                if (downloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }

                if (!readable) {
                    Spacer(Modifier.height(12.dp))
                    MessageBanner(
                        text = "This server serves PDFs, but the app reads EPUB only for now.",
                    )
                }

                failure?.let {
                    Spacer(Modifier.height(12.dp))
                    MessageBanner(text = it, error = true)
                }
            }

            reading?.let { progress ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Reading progress",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = percentText(progress.percent),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            progress.chapter?.takeIf { it.isNotBlank() }?.let { chapter ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = chapter,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progress.percent.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Updated ${relativeTime(progress.updatedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            book.description?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    SectionTitle("About")
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            item {
                SectionTitle("Details")
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        InfoRow("Publisher", book.publisher)
                        InfoRow("Published", book.publishedAt)
                        InfoRow("Language", book.language)
                        InfoRow("Size", formatBytes(book.sizeBytes))
                        InfoRow("Added", relativeTime(book.addedAt))
                    }
                }
            }
        }
    }

    if (shelvesOpen) {
        ShelfDialog(book = book, onDismiss = { shelvesOpen = false })
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShelfDialog(book: Book, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Api.shelves() } }
            .onSuccess { shelves = it }
            .onFailure { failure = it.message }
        loading = false
    }

    fun toggle(shelf: Shelf) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (book.id in shelf.bookIds) {
                        Api.removeFromShelf(shelf.id, book.id)
                    } else {
                        Api.addToShelf(shelf.id, book.id)
                    }
                }
            }.onSuccess { updated ->
                shelves = shelves.map { if (it.id == updated.id) updated else it }
            }.onFailure { failure = it.message }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to shelf") },
        text = {
            Column {
                when {
                    loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                    shelves.isEmpty() -> Text(
                        "No shelves yet. Create one in More → Shelves.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> shelves.forEach { shelf ->
                        val contains = book.id in shelf.bookIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle(shelf) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = contains, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = shelf.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (contains) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    }
                }
                failure?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
