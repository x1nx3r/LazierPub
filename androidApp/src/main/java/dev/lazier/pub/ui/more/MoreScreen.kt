package dev.lazier.pub.ui.more

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.Api
import dev.lazier.pub.data.CoverStore
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.data.Settings
import dev.lazier.pub.shared.ConfigResponse
import dev.lazier.pub.shared.Shelf
import dev.lazier.pub.shared.StatsResponse
import dev.lazier.pub.ui.SectionTitle
import dev.lazier.pub.ui.formatBytes
import dev.lazier.pub.ui.icons.LibraryIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings, statistics, and shelves: the maintenance corner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var url by rememberSaveable { mutableStateOf(Settings.serverUrl) }
    var token by rememberSaveable { mutableStateOf(Settings.token) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf<ConfigResponse?>(null) }

    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var shelfName by remember { mutableStateOf("") }
    var shelfError by remember { mutableStateOf<String?>(null) }

    fun testConnection() {
        scope.launch {
            busy = true
            status = withContext(Dispatchers.IO) {
                runCatching { Api.config() }
                    .fold(
                        onSuccess = { response ->
                            config = response
                            "Connected · ${response.bookCount} books · server ${response.version}"
                        },
                        onFailure = { it.message },
                    )
            }
            busy = false
        }
    }

    fun loadShelves() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Api.shelves() } }
                .onSuccess { shelves = it }
                .onFailure { shelfError = it.message }
        }
    }

    fun createShelf() {
        val name = shelfName.trim()
        if (name.isEmpty()) return
        scope.launch {
            shelfError = null
            runCatching { withContext(Dispatchers.IO) { Api.createShelf(name) } }
                .onSuccess { shelfName = ""; loadShelves() }
                .onFailure { shelfError = it.message }
        }
    }

    fun deleteShelf(shelf: Shelf) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Api.deleteShelf(shelf.id) } }
                .onFailure { shelfError = it.message }
            loadShelves()
        }
    }

    LaunchedEffect(Unit) {
        if (!LibraryStore.loaded) LibraryStore.refresh()
        loadShelves()
        testConnection()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("More")
                    Text(
                        text = "Settings and maintenance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { testConnection() }, enabled = !busy) {
                    Icon(Icons.Default.Refresh, contentDescription = "Test connection")
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BrandHeader(
                    status = status,
                    busy = busy,
                    version = versionName(context),
                )
            }

            item {
                SectionTitle(
                    title = "Server",
                    subtitle = "The API also drives Prowlarr and qBittorrent.",
                )
            }
            item {
                SettingsCard {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://10.0.2.2:8787") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                Settings.updateServer(url, token)
                                status = "Saved. Loading the library…"
                                scope.launch {
                                    LibraryStore.refresh()
                                    testConnection()
                                }
                            },
                        ) { Text("Save") }
                        OutlinedButton(onClick = { testConnection() }, enabled = !busy) {
                            Text(if (busy) "Testing…" else "Test")
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    title = "Appearance",
                    subtitle = "Theme, colour source, and grid density.",
                )
            }
            item {
                SettingsCard {
                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                            .forEachIndexed { index, (key, label) ->
                                SegmentedButton(
                                    selected = Settings.theme == key,
                                    onClick = { Settings.updateTheme(key) },
                                    shape = SegmentedButtonDefaults.itemShape(index, count = 3),
                                    icon = {},
                                ) { Text(label) }
                            }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(Modifier.height(10.dp))
                        SettingSwitch(
                            title = "Wallpaper colours",
                            subtitle = "Use the Android 12+ Material You palette.",
                            checked = Settings.dynamicColor,
                            onCheckedChange = { Settings.updateDynamicColor(it) },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Grid columns",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${Settings.columns}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = Settings.columns.toFloat(),
                        onValueChange = { Settings.updateColumns(it.toInt()) },
                        valueRange = 2f..5f,
                        steps = 2,
                    )
                }
            }

            item {
                SectionTitle(
                    title = "Library",
                    subtitle = "What the shelves show.",
                )
            }
            item {
                SettingsCard {
                    SettingSwitch(
                        title = "Show PDF books",
                        subtitle = "PDFs stay out of the library, recent, and history.",
                        checked = Settings.showPdf,
                        onCheckedChange = { Settings.updateShowPdf(it) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Statistics below still count every file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionTitle(
                    title = "Statistics",
                    subtitle = "Server-wide totals, including hidden PDFs.",
                )
            }
            item { StatsCard(LibraryStore.stats) }

            item {
                SectionTitle(
                    title = "Shelves",
                    subtitle = "Manual collections that live on the server.",
                )
            }
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = shelfName,
                            onValueChange = { shelfName = it },
                            label = { Text("New shelf") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { createShelf() }) {
                            Icon(Icons.Default.Add, contentDescription = "Create shelf")
                        }
                    }
                    if (shelves.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No shelves yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        shelves.forEachIndexed { index, shelf ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(shelf.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${shelf.bookIds.size} " +
                                            "${if (shelf.bookIds.size == 1) "book" else "books"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { deleteShelf(shelf) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete shelf")
                                }
                            }
                        }
                    }
                    shelfError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {
                SectionTitle(title = "Maintenance")
            }
            item {
                SettingsCard {
                    OutlinedButton(
                        onClick = {
                            CoverStore.forget()
                            scope.launch { LibraryStore.refresh() }
                            status = "Cover cache cleared."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Clear cover cache and refresh") }
                }
            }

            item {
                SectionTitle(title = "About")
            }
            item {
                SettingsCard {
                    Text(
                        text = "LazierPub ${versionName(context)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = config?.let { "Backend ${it.version} at ${it.booksDir}" }
                            ?: "Backend not contacted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "EPUB reader built on Readium. Library, progress and shelves come from the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(status: String?, busy: Boolean, version: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(LibraryIcon, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("LazierPub", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "EPUB light-novel library",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = version,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(10.dp))
    Surface(
        shape = CircleShape,
        color = if (status?.startsWith("Connected") == true) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (status?.startsWith("Connected") == true) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = when {
                busy -> "Testing connection…"
                status != null -> status
                else -> "Not connected yet"
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatsCard(stats: StatsResponse?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        if (stats == null) {
            Text(
                text = "No data yet.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell("Books", "${stats.books}", Modifier.weight(1f))
                    StatCell("EPUB", "${stats.epub}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell("PDF", "${stats.pdf}", Modifier.weight(1f))
                    StatCell("Series", "${stats.series}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCell("Authors", "${stats.authors}", Modifier.weight(1f))
                    StatCell("Covers", "${stats.withCovers}", Modifier.weight(1f))
                }
                StatCell("Library size", formatBytes(stats.totalBytes), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun versionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "0.1.0"
