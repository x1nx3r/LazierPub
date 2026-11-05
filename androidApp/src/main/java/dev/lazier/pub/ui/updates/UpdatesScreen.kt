package dev.lazier.pub.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.data.Settings
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.ui.BookRow
import dev.lazier.pub.ui.SectionTitle
import dev.lazier.pub.ui.StateMessage
import dev.lazier.pub.ui.icons.UpdatesIcon
import dev.lazier.pub.ui.relativeTime
import java.util.Calendar

/** What landed in the library lately, grouped by when it arrived. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onOpenBook: (String) -> Unit,
    onOpenBrowse: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (!LibraryStore.loaded) LibraryStore.refresh()
    }

    val recent = remember(LibraryStore.visibleBooks) {
        LibraryStore.visibleBooks.sortedByDescending { it.addedAt ?: 0L }.take(100)
    }
    val groups = remember(recent) { recent.groupBy { dayLabel(it.addedAt) } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Recent")
                    Text(
                        text = "Newest downloads first",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        if (recent.isEmpty()) {
            val hiddenPdf = !Settings.showPdf &&
                LibraryStore.books.any { it.kind == BookKind.PDF }
            StateMessage(
                title = if (hiddenPdf) "No EPUB books yet" else "Nothing here yet",
                message = if (hiddenPdf) {
                    "PDF books are hidden. Show them in More → Library."
                } else {
                    "Books appear here as soon as they finish downloading."
                },
                actionLabel = "Browse releases",
                onAction = onOpenBrowse,
                icon = UpdatesIcon,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (label, books) ->
                    item(key = "header-$label") {
                        SectionTitle(
                            title = label,
                            subtitle = "${books.size} ${if (books.size == 1) "book" else "books"}",
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(books, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            subtitle = rowSubtitle(book),
                        )
                    }
                }
            }
        }
    }
}

private fun rowSubtitle(book: Book): String = listOfNotNull(
    book.series ?: book.author,
    book.volume?.let { "Vol. $it" },
    relativeTime(book.addedAt),
).joinToString(" • ")

private fun dayLabel(millis: Long?): String {
    if (millis == null || millis <= 0) return "Earlier"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }

    fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    if (sameDay(now, then)) return "Today"

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(yesterday, then)) return "Yesterday"

    val weekAgo = now.timeInMillis - 7L * 24 * 60 * 60 * 1000
    return if (millis >= weekAgo) "Earlier this week" else "Earlier"
}
