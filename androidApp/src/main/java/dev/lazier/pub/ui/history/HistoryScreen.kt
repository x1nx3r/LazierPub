package dev.lazier.pub.ui.history

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.data.Settings
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.ui.ContinueCard
import dev.lazier.pub.ui.SectionTitle
import dev.lazier.pub.ui.StateMessage
import dev.lazier.pub.ui.icons.HistoryIcon

/** Everything with a saved position, most recently read first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenBook: (String) -> Unit,
    onOpenBrowse: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (!LibraryStore.loaded) LibraryStore.refresh() else LibraryStore.refreshProgress()
    }

    val items = LibraryStore.visibleContinueItems

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("History")
                    Text(
                        text = "Pick up where you left off",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        if (items.isEmpty()) {
            val hiddenPdf = !Settings.showPdf &&
                LibraryStore.continueItems.any { it.book.kind == BookKind.PDF }
            StateMessage(
                title = if (hiddenPdf) "No EPUB progress yet" else "Nothing in progress",
                message = if (hiddenPdf) {
                    "PDF books are hidden. Show them in More → Library."
                } else {
                    "Open a book and your position appears here."
                },
                actionLabel = "Browse releases",
                onAction = onOpenBrowse,
                icon = HistoryIcon,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionTitle(
                        title = "Continue reading",
                        subtitle = "${items.size} ${if (items.size == 1) "book" else "books"} in progress",
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
                items(items, key = { it.book.id }) { item ->
                    ContinueCard(item = item, onClick = { onOpenBook(item.book.id) })
                }
            }
        }
    }
}
