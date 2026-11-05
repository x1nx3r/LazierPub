package dev.lazier.pub.ui.downloads

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.Api
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.shared.Grab
import dev.lazier.pub.ui.MessageBanner
import dev.lazier.pub.ui.StateMessage
import dev.lazier.pub.ui.formatBytes
import dev.lazier.pub.ui.icons.DownloadIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The download queue, kept apart from Browse so discovery and queue management
 * do not share one scrolling list. qBittorrent reports progress, so this screen
 * polls while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pendingRemoval by remember { mutableStateOf<Grab?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            LibraryStore.refreshGrabs()
            delay(3_000)
        }
    }

    fun remove(grab: Grab) {
        scope.launch {
            failure = null
            runCatching { withContext(Dispatchers.IO) { Api.cancelGrab(grab.hash) } }
                .onFailure { failure = it.message ?: "Could not remove the download." }
            LibraryStore.refreshGrabs()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Downloads") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { scope.launch { LibraryStore.refreshGrabs() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh downloads")
                }
            },
        )

        failure?.let {
            MessageBanner(
                text = it,
                error = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (LibraryStore.grabs.isNotEmpty()) {
            LibraryStore.grabsError?.let {
                MessageBanner(
                    text = "qBittorrent: $it",
                    error = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (LibraryStore.grabs.isEmpty()) {
            val error = LibraryStore.grabsError
            if (error != null) {
                StateMessage(
                    title = "Cannot reach qBittorrent",
                    message = error,
                    actionLabel = "Retry",
                    onAction = { scope.launch { LibraryStore.refreshGrabs() } },
                    icon = Icons.Default.Warning,
                )
            } else {
                StateMessage(
                    title = "No downloads",
                    message = "Grab a release from Browse and it will appear here.",
                    actionLabel = "Back to Browse",
                    onAction = onBack,
                    icon = DownloadIcon,
                )
            }
        } else {
            val sorted = LibraryStore.grabs.sortedWith(
                compareBy<Grab> { it.progress >= 1.0 }.thenByDescending { it.progress },
            )
            val active = LibraryStore.grabs.count { it.progress < 1.0 }
            val seeding = LibraryStore.grabs.size - active

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (active > 0) StatPill("$active active", primary = true)
                        if (seeding > 0) StatPill("$seeding seeding", primary = false)
                    }
                }
                items(sorted, key = { it.hash }) { grab ->
                    DownloadCard(grab = grab, onRemove = { pendingRemoval = grab })
                }
            }
        }
    }

    val removing = pendingRemoval
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove download?") },
            text = {
                Text(
                    "This removes the torrent from qBittorrent. " +
                        "Files already downloaded stay in the library.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        remove(removing)
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun StatPill(text: String, primary: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        contentColor = if (primary) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DownloadCard(grab: Grab, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = grab.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateTag(friendlyState(grab.state))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(grab.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove download")
                }
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { grab.progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(8.dp))
            val line = listOfNotNull(
                formatBytes(grab.sizeBytes).takeIf { it.isNotBlank() },
                grab.speedBytesPerSecond?.takeIf { it > 0 }?.let { "${formatBytes(it)}/s" },
                formatEta(grab.etaSeconds)?.let { "ETA $it" },
                grab.category?.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
            Text(
                text = line.ifBlank { "Waiting for the client…" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StateTag(state: String) {
    val (container, content) = when (state) {
        "Downloading" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        "Seeding" -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        "Stalled" -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = state,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** qBittorrent state names, in words. */
private fun friendlyState(state: String): String = when {
    state.contains("stalled", ignoreCase = true) && state.contains("UP", ignoreCase = true) -> "Seeding"
    state.contains("download", ignoreCase = true) -> "Downloading"
    state.contains("stalled", ignoreCase = true) -> "Stalled"
    state.contains("check", ignoreCase = true) -> "Checking"
    state.contains("UP", ignoreCase = true) -> "Seeding"
    else -> state
}

/** Drop the "infinite" ETA qBittorrent uses when it cannot estimate. */
private fun formatEta(seconds: Long?): String? {
    if (seconds == null || seconds <= 0 || seconds >= 7L * 24 * 3600) return null
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
