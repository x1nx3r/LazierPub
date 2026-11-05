package dev.lazier.pub.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.data.Settings
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.BookKind
import dev.lazier.pub.ui.BookCard
import dev.lazier.pub.ui.SeriesCard
import dev.lazier.pub.ui.StateMessage
import dev.lazier.pub.ui.icons.LibraryIcon
import kotlinx.coroutines.launch

/**
 * The library, in the shape Mihon uses: a wall of covers and a quick way to
 * switch between series and individual volumes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenSeries: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBrowse: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var seriesMode by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf("title") }
    var descending by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (!LibraryStore.loaded) LibraryStore.refresh()
    }

    val visibleBooks = LibraryStore.visibleBooks
    val visibleSeries = LibraryStore.visibleSeries
    val hiddenCount = LibraryStore.books.count { it.kind == BookKind.PDF }
    val hiddenPdf = !Settings.showPdf && hiddenCount > 0

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library") },
            actions = {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Library menu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf(
                        "title" to "Sort by title",
                        "author" to "Sort by author",
                        "added" to "Sort by added",
                        "size" to "Sort by size",
                    ).forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = {
                                if (sort == key) Icon(Icons.Default.Check, contentDescription = null)
                            },
                            onClick = { sort = key; menuOpen = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (descending) "Ascending" else "Descending") },
                        onClick = { descending = !descending; menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh library") },
                        onClick = {
                            menuOpen = false
                            scope.launch { LibraryStore.refresh() }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menuOpen = false; onOpenSettings() },
                    )
                }
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search title, author, series") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (seriesMode) {
                        "${visibleSeries.size} series"
                    } else {
                        "${visibleBooks.size} volumes"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = buildString {
                        append("${visibleBooks.size} ${if (visibleBooks.size == 1) "book" else "books"}")
                        if (hiddenPdf) append(" · $hiddenCount PDF${if (hiddenCount == 1) "" else "s"} hidden")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = seriesMode,
                    onClick = { seriesMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                ) { Text("Series") }
                SegmentedButton(
                    selected = !seriesMode,
                    onClick = { seriesMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                ) { Text("Volumes") }
            }
        }

        val filteredSeries = remember(visibleSeries, query) {
            if (query.isBlank()) {
                visibleSeries
            } else {
                visibleSeries.filter { it.name.contains(query, ignoreCase = true) }
            }
        }
        val filteredBooks = remember(visibleBooks, query, sort, descending) {
            filterBooks(visibleBooks, query, sort, descending)
        }

        when {
            LibraryStore.loading && !LibraryStore.loaded -> LibrarySkeleton()

            LibraryStore.error != null && LibraryStore.books.isEmpty() -> StateMessage(
                title = "Cannot reach the server",
                message = LibraryStore.error,
                actionLabel = "Retry",
                onAction = { scope.launch { LibraryStore.refresh() } },
                icon = Icons.Default.Warning,
            )

            seriesMode && filteredSeries.isEmpty() -> EmptyLibrary(
                query = query,
                hiddenPdf = hiddenPdf,
                hiddenCount = hiddenCount,
                onOpenBrowse = onOpenBrowse,
                onOpenSettings = onOpenSettings,
                onRefresh = { scope.launch { LibraryStore.refresh() } },
            )

            !seriesMode && filteredBooks.isEmpty() -> EmptyLibrary(
                query = query,
                hiddenPdf = hiddenPdf,
                hiddenCount = hiddenCount,
                onOpenBrowse = onOpenBrowse,
                onOpenSettings = onOpenSettings,
                onRefresh = { scope.launch { LibraryStore.refresh() } },
            )

            else -> PullToRefreshBox(
                state = pullState,
                isRefreshing = LibraryStore.loading && LibraryStore.loaded,
                onRefresh = { scope.launch { LibraryStore.refresh() } },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (seriesMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(Settings.columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filteredSeries, key = { it.name }) { series ->
                            SeriesCard(
                                series = series,
                                onClick = { onOpenSeries(series.name) },
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(Settings.columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filteredBooks, key = { it.id }) { book ->
                            BookCard(book = book, onClick = { onOpenBook(book.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(
    query: String,
    hiddenPdf: Boolean,
    hiddenCount: Int,
    onOpenBrowse: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        query.isNotBlank() -> StateMessage(
            title = "Nothing matches \"$query\"",
            message = "Try another title, author, or series.",
            icon = Icons.Default.Search,
        )

        hiddenPdf -> StateMessage(
            title = "No EPUB books yet",
            message = "$hiddenCount PDF${if (hiddenCount == 1) " is" else "s are"} hidden. " +
                "Show them in More → Library.",
            actionLabel = "Open settings",
            onAction = onOpenSettings,
            icon = LibraryIcon,
        )

        else -> StateMessage(
            title = "Your library is empty",
            message = "Search for a release and grab it. It lands here when it finishes.",
            actionLabel = "Browse releases",
            onAction = onOpenBrowse,
            icon = LibraryIcon,
        )
    }
}

@Composable
private fun LibrarySkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(Settings.columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(9) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.66f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.82f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.48f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}

private fun filterBooks(books: List<Book>, query: String, sort: String, descending: Boolean): List<Book> {
    val filtered = if (query.isBlank()) {
        books
    } else {
        books.filter { book ->
            book.title.contains(query, ignoreCase = true) ||
                book.author?.contains(query, ignoreCase = true) == true ||
                book.series?.contains(query, ignoreCase = true) == true
        }
    }

    val comparator: Comparator<Book> = when (sort) {
        "author" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.author ?: "\uFFFF" }
        "added" -> compareBy { it.addedAt ?: 0L }
        "size" -> compareBy { it.sizeBytes ?: 0L }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }
    return if (descending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
}
