package dev.lazier.pub.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.ui.BookRow
import dev.lazier.pub.ui.CoverImage
import dev.lazier.pub.ui.SectionTitle
import dev.lazier.pub.ui.StateMessage
import dev.lazier.pub.ui.Tag
import dev.lazier.pub.ui.icons.LibraryIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    name: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val books = remember(name, LibraryStore.visibleBooks) { LibraryStore.booksInSeries(name) }

    LaunchedEffect(Unit) {
        if (!LibraryStore.loaded) LibraryStore.refresh()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        if (books.isEmpty()) {
            StateMessage(
                title = "No volumes in this series",
                message = "The files may have been moved or hidden by the PDF filter.",
                icon = LibraryIcon,
            )
            return@Column
        }

        val cover = books.firstOrNull { it.hasCover } ?: books.first()
        val authors = books.mapNotNull { it.author }.distinct().joinToString(", ")
        val kinds = books.map { it.kind }.distinct()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        bookId = cover.id.takeIf { cover.hasCover },
                        width = 400,
                        modifier = Modifier
                            .width(112.dp)
                            .height(168.dp)
                            .shadow(6.dp, RoundedCornerShape(12.dp)),
                        corner = 12.dp,
                        label = name,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (authors.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = authors,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${books.size} ${if (books.size == 1) "volume" else "volumes"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (kinds.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                kinds.forEach { kind ->
                                    Tag(kind.name)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    title = "Volumes",
                    subtitle = "Tap a volume to open it",
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
            }

            items(books, key = { it.id }) { book ->
                BookRow(
                    book = book,
                    onClick = { onOpenBook(book.id) },
                    subtitle = listOfNotNull(
                        book.volume?.let { "Vol. $it" },
                        book.author,
                        book.kind.name,
                    ).joinToString(" • "),
                    trailingPercent = LibraryStore.progressFor(book.id)?.percent,
                )
            }
        }
    }
}
