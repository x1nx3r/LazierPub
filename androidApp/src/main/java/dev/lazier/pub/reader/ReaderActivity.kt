package dev.lazier.pub.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import dev.lazier.pub.R
import dev.lazier.pub.data.LibraryStore
import dev.lazier.pub.data.Settings
import dev.lazier.pub.shared.Book
import dev.lazier.pub.shared.ReadingProgress
import dev.lazier.pub.ui.percentText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/** UI state that both chrome views observe. */
class ReaderUi {
    var chromeVisible by mutableStateOf(true)
    var percent by mutableFloatStateOf(0f)
    var chapter by mutableStateOf<String?>(null)
    var tocOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var toc by mutableStateOf<List<TocEntry>>(emptyList())
    var failure by mutableStateOf<String?>(null)
}

data class TocEntry(val depth: Int, val title: String, val link: Link)

/**
 * Readium renders EPUBs in a Fragment; this Activity gives it a lifecycle of
 * its own. Compose draws the thin chrome above and below, so the page keeps
 * almost the whole screen.
 */
class ReaderActivity : FragmentActivity() {

    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
    private var saveJob: Job? = null
    private var locatorJob: Job? = null
    private var chromeJob: Job? = null
    private var lastLocator: Locator? = null
    private var appliedScroll = false

    private lateinit var bookId: String
    private lateinit var bookTitle: String
    private lateinit var topChrome: ComposeView
    private lateinit var bottomChrome: ComposeView
    private lateinit var rootView: FrameLayout
    private lateinit var readerContainer: FragmentContainerView
    private var topBand = 0
    private var bottomBand = 0

    private val ui = ReaderUi()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The navigator cannot be restored without its Publication, so this
        // screen always starts fresh and the server position brings the reader
        // back to the last page.
        super.onCreate(null)
        enableEdgeToEdge()

        bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
        bookTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Reader"
        appliedScroll = ReaderSettings.config.scroll
        ui.percent = intent.getDoubleExtra(EXTRA_PERCENT, 0.0).toFloat()

        setContentView(buildViews())
        updateSystemBars()

        // The bars slide over reserved bands, so the page never changes size.
        lifecycleScope.launch {
            snapshotFlow { ui.chromeVisible }.collect { visible -> animateChrome(visible) }
        }
        lifecycleScope.launch {
            snapshotFlow { ReaderSettings.config.backgroundColor }.collect { color ->
                rootView.setBackgroundColor(color)
            }
        }

        // The bars retreat on their own, but never while a sheet is open.
        scheduleChromeHide()
        lifecycleScope.launch {
            snapshotFlow { ui.tocOpen || ui.settingsOpen }.collect { open ->
                if (open) chromeJob?.cancel() else scheduleChromeHide()
            }
        }

        val path = intent.getStringExtra(EXTRA_FILE)
        if (path == null) {
            ui.failure = "The book file is missing."
        } else {
            open(File(path))
        }
    }

    private fun buildViews(): View {
        // The page keeps one size forever. The bars sit in reserved bands and
        // slide over them, so showing the chrome never reflows the text.
        val root = FrameLayout(this).apply {
            setBackgroundColor(ReaderSettings.config.backgroundColor)
            // The margins and the reserved bands belong to the reader too.
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP -> {
                        onReaderTap(event.x)
                        true
                    }
                    else -> true
                }
            }
        }
        rootView = root

        readerContainer = FragmentContainerView(this).apply { id = R.id.reader_container }
        root.addView(readerContainer, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        topChrome = ComposeView(this).apply {
            setContent {
                ReaderTheme(ReaderSettings.config) {
                    ReaderTopBar(
                        ui = ui,
                        title = bookTitle,
                        onBack = { finish() },
                        onGo = { link ->
                            navigator?.go(link)
                            ui.tocOpen = false
                        },
                        onPreferences = { applyPreferences() },
                    )
                }
            }
        }
        root.addView(topChrome, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))

        bottomChrome = ComposeView(this).apply {
            setContent {
                ReaderTheme(ReaderSettings.config) { ReaderBottomBar(ui) }
            }
        }
        root.addView(bottomChrome, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        topChrome.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val height = bottom - top
            if (height > 0 && height != topBand) {
                topBand = height
                updateMargins()
            }
        }
        bottomChrome.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val height = bottom - top
            if (height > 0 && height != bottomBand) {
                bottomBand = height
                updateMargins()
            }
        }

        return root
    }

    private fun open(file: File) {
        lifecycleScope.launch {
            runCatching {
                val httpClient = DefaultHttpClient()
                val assets = AssetRetriever(contentResolver, httpClient)
                val parser = DefaultPublicationParser(this@ReaderActivity, httpClient, assets, null)
                val opener = PublicationOpener(parser, emptyList())

                val asset = assets.retrieve(file).getOrNull()
                    ?: error("Cannot open this file. It may be damaged.")
                opener.open(asset, allowUserInteraction = false).getOrNull()
                    ?: error("This file is not a readable EPUB.")
            }.onSuccess { opened ->
                publication = opened
                ui.toc = flatten(opened.tableOfContents)
                showNavigator(opened, savedLocator())
            }.onFailure { error ->
                ui.failure = error.message ?: "Cannot open this book."
            }
        }
    }

    private fun savedLocator(): Locator? =
        intent.getStringExtra(EXTRA_LOCATOR)?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }

    private fun showNavigator(opened: Publication, initial: Locator?) {
        val factory = EpubNavigatorFactory(opened).createFragmentFactory(
            initialLocator = initial,
            initialPreferences = ReaderSettings.preferences(),
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    // External links stay inert: this reader is the whole screen.
                }
            },
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(page: Int, totalPages: Int, locator: Locator) {
                    onLocatorChanged(locator)
                }
            },
        )

        supportFragmentManager.fragmentFactory = factory
        val fragment = factory.instantiate(
            EpubNavigatorFragment::class.java.classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment

        supportFragmentManager.beginTransaction()
            .replace(R.id.reader_container, fragment)
            .commitNow()

        attach(fragment)
    }

    private fun attach(fragment: EpubNavigatorFragment) {
        navigator = fragment

        fragment.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean =
                onReaderTap(event.point.x + readerContainer.left)
        })

        locatorJob?.cancel()
        locatorJob = lifecycleScope.launch {
            fragment.currentLocator.collect { locator -> onLocatorChanged(locator) }
        }
    }

    private fun onLocatorChanged(locator: Locator) {
        lastLocator = locator
        ui.percent = (locator.locations.totalProgression ?: 0.0).toFloat().coerceIn(0f, 1f)
        ui.chapter = locator.title?.takeIf { it.isNotBlank() }

        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(1_500)
            persist()
        }
    }

    private suspend fun persist() {
        val locator = lastLocator ?: return
        LibraryStore.saveProgress(
            bookId = bookId,
            locatorJson = locator.toJSON().toString(),
            percent = locator.locations.totalProgression ?: ui.percent.toDouble(),
            chapter = locator.title,
            finished = false,
        )
    }

    /** Left and right thirds turn the page; the middle toggles the chrome. */
    private fun onReaderTap(x: Float): Boolean {
        val width = window.decorView.width.toFloat()
        if (width <= 0f) return false

        return when {
            x < width * 0.33f -> {
                hideChrome()
                navigator?.goBackward(false) ?: false
            }
            x > width * 0.67f -> {
                hideChrome()
                navigator?.goForward(false) ?: false
            }
            else -> {
                toggleChrome()
                true
            }
        }
    }

    /** Side and vertical margins, on top of the space the bars reserve. */
    private fun updateMargins() {
        if (!::readerContainer.isInitialized) return

        val config = ReaderSettings.config
        val metrics = resources.displayMetrics
        val side = (metrics.widthPixels * SIDE_MARGIN_FRACTION * config.margins).toInt()
        val vertical = (metrics.heightPixels * VERTICAL_MARGIN_FRACTION * config.verticalMargins).toInt()
        val params = readerContainer.layoutParams as? FrameLayout.LayoutParams ?: return

        val top = topBand + vertical
        val bottom = bottomBand + vertical
        if (params.leftMargin == side && params.rightMargin == side &&
            params.topMargin == top && params.bottomMargin == bottom
        ) {
            return
        }

        params.leftMargin = side
        params.rightMargin = side
        params.topMargin = top
        params.bottomMargin = bottom
        readerContainer.layoutParams = params
    }

    private fun applyPreferences() {
        val config = ReaderSettings.config
        if (config.scroll != appliedScroll) {
            // Readium changes paged/scroll by rebuilding the navigator; a
            // preference update alone leaves the old view in place.
            appliedScroll = config.scroll
            val opened = publication
            if (opened != null) {
                val position = lastLocator
                saveJob?.cancel()
                lifecycleScope.launch { persist() }
                showNavigator(opened, position)
            }
        } else {
            navigator?.submitPreferences(ReaderSettings.preferences())
        }
        updateMargins()
        updateSystemBars()
    }

    private fun toggleChrome() {
        if (ui.chromeVisible) hideChrome() else showChrome()
    }

    private fun showChrome() {
        ui.chromeVisible = true
        scheduleChromeHide()
    }

    private fun hideChrome() {
        chromeJob?.cancel()
        chromeJob = null
        ui.chromeVisible = false
    }

    private fun animateChrome(visible: Boolean) {
        val duration = 220L
        val curve = DecelerateInterpolator()
        val a11y = if (visible) {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        topChrome.importantForAccessibility = a11y
        bottomChrome.importantForAccessibility = a11y
        topChrome.animate()
            .translationY(if (visible) 0f else -topChrome.height.toFloat())
            .alpha(if (visible) 1f else 0f)
            .setDuration(duration)
            .setInterpolator(curve)
            .start()
        bottomChrome.animate()
            .translationY(if (visible) 0f else bottomChrome.height.toFloat())
            .alpha(if (visible) 1f else 0f)
            .setDuration(duration)
            .setInterpolator(curve)
            .start()
    }

    private fun scheduleChromeHide() {
        chromeJob?.cancel()
        if (!ui.chromeVisible) return
        chromeJob = lifecycleScope.launch {
            delay(CHROME_TIMEOUT_MS)
            if (ui.chromeVisible && !ui.tocOpen && !ui.settingsOpen) {
                chromeJob = null
                hideChrome()
            }
        }
    }

    /** Light page means dark status bar icons, and the other way round. */
    private fun updateSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val lightIcons = !ReaderSettings.config.dark
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
    }

    override fun onStop() {
        super.onStop()
        saveJob?.cancel()
        lifecycleScope.launch { persist() }
    }

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_FILE = "file"
        private const val EXTRA_LOCATOR = "locator"
        private const val EXTRA_PERCENT = "percent"

        fun intent(context: Context, book: Book, file: File, progress: ReadingProgress?): Intent =
            Intent(context, ReaderActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, book.id)
                putExtra(EXTRA_TITLE, book.title)
                putExtra(EXTRA_FILE, file.absolutePath)
                putExtra(EXTRA_LOCATOR, progress?.locator?.toString())
                putExtra(EXTRA_PERCENT, progress?.percent ?: 0.0)
            }

        private fun flatten(links: List<Link>, depth: Int = 0): List<TocEntry> =
            links.flatMap { link ->
                listOf(TocEntry(depth, link.title ?: "Untitled", link)) + flatten(link.children, depth + 1)
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    ui: ReaderUi,
    title: String,
    onBack: () -> Unit,
    onGo: (Link) -> Unit,
    onPreferences: () -> Unit,
) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            title = {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { ui.tocOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents")
                }
                IconButton(onClick = { ui.settingsOpen = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Display options")
                }
            },
        )

    if (ui.tocOpen) {
        AlertDialog(
            onDismissRequest = { ui.tocOpen = false },
            title = { Text("Contents") },
            text = {
                if (ui.toc.isEmpty()) {
                    Text("This book has no table of contents.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(ui.toc) { entry ->
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onGo(entry.link) }
                                    .padding(
                                        start = (8 + entry.depth * 14).dp,
                                        top = 9.dp,
                                        bottom = 9.dp,
                                        end = 8.dp,
                                    ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ui.tocOpen = false }) { Text("Close") }
            },
        )
    }

    if (ui.settingsOpen) {
        ReaderDisplayDialog(
            config = ReaderSettings.config,
            onChange = { value ->
                ReaderSettings.save(value)
                onPreferences()
            },
            onDone = { ui.settingsOpen = false },
        )
    }

    ui.failure?.let { failure ->
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Cannot open book") },
            text = { Text(failure) },
            confirmButton = { TextButton(onClick = onBack) { Text("Close") } },
        )
    }
}

@Composable
private fun ReaderBottomBar(ui: ReaderUi) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            LinearProgressIndicator(
                progress = { ui.percent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = ui.chapter ?: "Reading",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = percentText(ui.percent.toDouble()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** How long the bars stay after they appear, in milliseconds. */
private const val CHROME_TIMEOUT_MS = 3_500L

/** Share of the screen width that a full side-margin setting uses. */
private const val SIDE_MARGIN_FRACTION = 0.15f

/** Share of the screen height that a full vertical-margin setting uses. */
private const val VERTICAL_MARGIN_FRACTION = 0.08f
