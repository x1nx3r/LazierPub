package dev.lazier.pub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.lazier.pub.data.CoverStore
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.data.Settings
import dev.lazier.pub.reader.ReaderSettings
import dev.lazier.pub.ui.LazierTheme
import dev.lazier.pub.ui.StateMessage
import dev.lazier.pub.ui.book.BookDetailScreen
import dev.lazier.pub.ui.browse.BrowseScreen
import dev.lazier.pub.ui.downloads.DownloadsScreen
import dev.lazier.pub.ui.history.HistoryScreen
import dev.lazier.pub.ui.icons.BrowseIcon
import dev.lazier.pub.ui.icons.HistoryIcon
import dev.lazier.pub.ui.icons.LibraryIcon
import dev.lazier.pub.ui.icons.MoreIcon
import dev.lazier.pub.ui.icons.UpdatesIcon
import dev.lazier.pub.ui.library.LibraryScreen
import dev.lazier.pub.ui.more.MoreScreen
import dev.lazier.pub.ui.series.SeriesDetailScreen
import dev.lazier.pub.ui.updates.UpdatesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(applicationContext)
        ReaderSettings.init(applicationContext)
        CoverStore.init(applicationContext)
        enableEdgeToEdge()

        setContent {
            LazierTheme(Settings.theme, Settings.dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }
}

sealed interface Route {
    data class Series(val name: String) : Route
    data class Book(val id: String) : Route
    data object Downloads : Route
}

/**
 * Five tabs, in Mihon's order: the library, what is new, what is in progress,
 * discovery, and everything else. Detail screens push over a tab and hide the
 * bar, so the back gesture returns to where the reader started.
 */
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }

    BackHandler(enabled = stack.isNotEmpty()) {
        stack.removeAt(stack.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (stack.isEmpty()) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    TabItem("Library", LibraryIcon, 0, tab) { tab = 0 }
                    TabItem("Recent", UpdatesIcon, 1, tab) { tab = 1 }
                    TabItem("History", HistoryIcon, 2, tab) { tab = 2 }
                    TabItem("Browse", BrowseIcon, 3, tab) { tab = 3 }
                    TabItem("More", MoreIcon, 4, tab) { tab = 4 }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val route = stack.lastOrNull()
            when {
                route is Route.Series -> SeriesDetailScreen(
                    name = route.name,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpenBook = { stack.add(Route.Book(it)) },
                )

                route is Route.Book -> {
                    val book = LibraryStore.book(route.id)
                    if (book == null) {
                        LaunchedEffect(route.id) {
                            if (!LibraryStore.loaded) LibraryStore.refresh()
                        }
                        StateMessage(
                            title = "Loading book…",
                            message = LibraryStore.error,
                            actionLabel = "Back",
                            onAction = { stack.removeAt(stack.lastIndex) },
                        )
                    } else {
                        BookDetailScreen(
                            book = book,
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }
                }

                route is Route.Downloads -> DownloadsScreen(
                    onBack = { stack.removeAt(stack.lastIndex) },
                )

                else -> when (tab) {
                    0 -> LibraryScreen(
                        onOpenSeries = { stack.add(Route.Series(it)) },
                        onOpenBook = { stack.add(Route.Book(it)) },
                        onOpenSettings = { tab = 4 },
                        onOpenBrowse = { tab = 3 },
                    )

                    1 -> UpdatesScreen(
                        onOpenBook = { stack.add(Route.Book(it)) },
                        onOpenBrowse = { tab = 3 },
                    )
                    2 -> HistoryScreen(
                        onOpenBook = { stack.add(Route.Book(it)) },
                        onOpenBrowse = { tab = 3 },
                    )
                    3 -> BrowseScreen(onOpenDownloads = { stack.add(Route.Downloads) })
                    else -> MoreScreen()
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    label: String,
    icon: ImageVector,
    index: Int,
    selected: Int,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected == index,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}
