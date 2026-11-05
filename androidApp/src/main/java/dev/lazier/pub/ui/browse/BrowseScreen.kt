package dev.lazier.pub.ui.browse

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.Api
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.shared.Indexer
import dev.lazier.pub.shared.SearchResult
import dev.lazier.pub.ui.MessageBanner
import dev.lazier.pub.ui.SectionTitle
import dev.lazier.pub.ui.StateMessage
import dev.lazier.pub.ui.formatBytes
import dev.lazier.pub.ui.icons.BrowseIcon
import dev.lazier.pub.ui.icons.DownloadIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The analogue of Mihon's Browse: pick one source or all of them, find a
 * release, then grab it. Prowlarr does the fan-out, so adding an indexer adds a
 * chip with no client change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onOpenDownloads: () -> Unit) {
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var indexers by remember { mutableStateOf<List<Indexer>>(emptyList()) }
    var sourceId by rememberSaveable { mutableStateOf(ALL_SOURCES) }
    var formatFilter by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var sourcesFailure by remember { mutableStateOf<String?>(null) }
    // Stale responses must not replace a newer search, e.g. after a source tap.
    var requestId by remember { mutableIntStateOf(0) }

    fun search() {
        val text = query.trim()
        if (text.isEmpty()) return
        val id = ++requestId
        val chosenSource = sourceId
        scope.launch {
            searching = true
            failure = null
            message = null
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    Api.search(
                        query = text,
                        indexerIds = if (chosenSource == ALL_SOURCES) emptyList() else listOf(chosenSource),
                    )
                }
            }
            if (id != requestId) return@launch
            outcome
                .onSuccess { response ->
                    results = response.results
                    searched = true
                    message = null
                    val present = response.results.flatMap { it.formats }.toSet()
                    if (formatFilter == NO_FORMAT && response.results.all { it.formats.isNotEmpty() }) {
                        formatFilter = ""
                    } else if (formatFilter.isNotEmpty() && formatFilter != NO_FORMAT && formatFilter !in present) {
                        formatFilter = ""
                    }
                }
                .onFailure { failure = it.message }
            searching = false
        }
    }

    fun chooseSource(id: Int) {
        if (sourceId == id) return
        sourceId = id
        // A pick during the first search still has to replace those results.
        if (query.isNotBlank() && (searched || searching)) search()
    }

    fun grab(result: SearchResult) {
        scope.launch {
            failure = null
            runCatching { withContext(Dispatchers.IO) { Api.grab(result.downloadUrl, result.title, null) } }
                .onSuccess {
                    message = "Added to the queue."
                    LibraryStore.refreshGrabs()
                }
                .onFailure { failure = it.message }
        }
    }

    LaunchedEffect(Unit) {
        LibraryStore.refreshGrabs()
        runCatching { withContext(Dispatchers.IO) { Api.indexers() } }
            .onSuccess { indexers = it }
            .onFailure { sourcesFailure = it.message }
    }

    val availableFormats = results.flatMap { it.formats }.distinct().sorted()
    val hasUnspecified = results.any { it.formats.isEmpty() }
    val visibleResults = when (formatFilter) {
        "" -> results
        NO_FORMAT -> results.filter { it.formats.isEmpty() }
        else -> results.filter { formatFilter in it.formats }
    }
    val sourceCount = results.mapNotNull { it.indexer }.distinct().size

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Browse") },
            actions = {
                val active = LibraryStore.grabs.count { it.progress < 1.0 }
                BadgedBox(
                    badge = {
                        if (active > 0) {
                            Badge { Text("$active") }
                        }
                    },
                ) {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(DownloadIcon, contentDescription = "Downloads")
                    }
                }
            },
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search releases") },
            singleLine = true,
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = { search() }, enabled = query.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Search",
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (indexers.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = sourceId == ALL_SOURCES,
                        onClick = { chooseSource(ALL_SOURCES) },
                        label = { Text("All sources") },
                    )
                }
                items(indexers, key = { it.id }) { indexer ->
                    FilterChip(
                        selected = sourceId == indexer.id,
                        onClick = { chooseSource(indexer.id) },
                        label = { Text(indexer.name) },
                    )
                }
            }
        } else if (sourcesFailure != null) {
            MessageBanner(
                text = "Sources unavailable. ${sourcesFailure?.substringBefore(" [")}",
                error = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (results.isNotEmpty() && (availableFormats.isNotEmpty() || hasUnspecified)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = formatFilter.isEmpty(),
                        onClick = { formatFilter = "" },
                        label = { Text("All formats") },
                    )
                }
                items(availableFormats) { format ->
                    FilterChip(
                        selected = formatFilter == format,
                        onClick = { formatFilter = format },
                        label = { Text(format) },
                    )
                }
                if (hasUnspecified) {
                    item {
                        FilterChip(
                            selected = formatFilter == NO_FORMAT,
                            onClick = { formatFilter = NO_FORMAT },
                            label = { Text("Unspecified") },
                        )
                    }
                }
            }
        }

        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(CircleShape),
            )
        }

        message?.let {
            MessageBanner(
                text = it,
                error = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        failure?.let {
            MessageBanner(
                text = it,
                error = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when {
            results.isEmpty() && !searching && !searched -> StateMessage(
                title = "Find your next book",
                message = "Search every enabled source at once, then filter by format.",
                icon = BrowseIcon,
            )

            searched && visibleResults.isEmpty() && !searching -> StateMessage(
                title = if (formatFilter.isNotEmpty()) "No releases match this format" else "No releases found",
                message = "Try a shorter query or another source.",
                icon = Icons.Default.Search,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (visibleResults.isNotEmpty()) {
                    item {
                        val count = visibleResults.size
                        val sources = when (sourceCount) {
                            0 -> ""
                            1 -> " · 1 source"
                            else -> " · $sourceCount sources"
                        }
                        SectionTitle(
                            title = "$count ${if (count == 1) "release" else "releases"}$sources",
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(visibleResults) { result ->
                        ReleaseCard(result = result, onGrab = { grab(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(result: SearchResult, onGrab: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FormatTile(result.formats)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOfNotNull(
                        result.indexer,
                        result.protocol?.replaceFirstChar { it.uppercase() },
                        formatBytes(result.sizeBytes).takeIf { it.isNotBlank() },
                        result.publishDate?.take(10),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    result.seeders?.let {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    result.leechers?.takeIf { it > 0 }?.let {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    result.grabs?.takeIf { it > 0 }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$it grabs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (result.categories.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = result.categories.take(2).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                FilledTonalButton(
                    onClick = onGrab,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Grab")
                }
                if (result.freeleech) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Text(
                            text = "FREE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Colour-coded stand-in cover: the format on the left of every release. */
@Composable
private fun FormatTile(formats: List<String>) {
    val primary = formats.firstOrNull()
    val background = when (primary) {
        "EPUB" -> Color(0xFF2E7D32)
        "PDF" -> Color(0xFFB3261E)
        "CBZ", "CBR" -> Color(0xFF6A1B9A)
        "MOBI", "AZW3", "AZW" -> Color(0xFF00695C)
        "MP3", "M4B", "FLAC" -> Color(0xFFEF6C00)
        "ZIP", "RAR", "7Z" -> Color(0xFF455A64)
        null -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.secondary
    }
    val foreground = if (primary == null) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }

    Surface(
        modifier = Modifier
            .width(54.dp)
            .height(74.dp),
        shape = RoundedCornerShape(12.dp),
        color = background,
        contentColor = foreground,
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = primary ?: "—",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = foreground,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (formats.size > 1) {
                Text(
                    text = "+${formats.size - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Sentinel: indexers have positive ids. */
private const val ALL_SOURCES = 0

/** Filter value for releases whose name does not state a format. */
private const val NO_FORMAT = "__none__"
