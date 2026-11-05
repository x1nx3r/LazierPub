package dev.lazier.pub.server

/**
 * Runtime configuration. Everything comes from the environment so the same
 * image runs on a laptop and on the server without a rebuild.
 */
data class ProwlarrConfig(
    val baseUrl: String,
    val apiKey: String,
    /** One Newznab category. Prowlarr rejects a comma-separated list. */
    val bookCategories: String = "7000",
)

data class QbittorrentConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val category: String,
)

data class Config(
    val port: Int,
    val booksDir: String,
    val coversDir: String,
    /** Writable directory for progress.json and shelves.json. */
    val dataDir: String,
    val token: String?,
    /** Optional password accepted by POST /auth/login. */
    val password: String?,
    val prowlarr: ProwlarrConfig?,
    val qbittorrent: QbittorrentConfig?,
)

/**
 * Tailnet addresses are the defaults, because that is where the services run.
 * Set the variables to override them.
 */
fun loadConfig(env: Map<String, String> = System.getenv()): Config {
    fun value(key: String): String? = env[key]?.takeIf { it.isNotBlank() }

    val prowlarr = value("PROWLARR_API_KEY")?.let { apiKey ->
        ProwlarrConfig(
            baseUrl = value("PROWLARR_URL") ?: "http://100.88.88.88:9696",
            apiKey = apiKey,
            bookCategories = value("PROWLARR_BOOK_CATEGORIES") ?: "7000",
        )
    }

    val qbittorrent = value("QBITTORRENT_USERNAME")?.let { username ->
        QbittorrentConfig(
            baseUrl = value("QBITTORRENT_URL") ?: "http://100.88.88.88:8081",
            username = username,
            password = value("QBITTORRENT_PASSWORD") ?: "",
            category = value("QBITTORRENT_CATEGORY") ?: "novels",
        )
    }

    return Config(
        port = value("PORT")?.toIntOrNull() ?: 8787,
        booksDir = value("BOOKS_DIR") ?: "/data/novels",
        // Writable cache for extracted covers. Keep it off the read-only books mount.
        coversDir = value("COVERS_DIR") ?: "/data/covers",
        dataDir = value("DATA_DIR") ?: "/data",
        token = value("LAZIER_TOKEN"),
        password = value("LAZIER_PASSWORD"),
        prowlarr = prowlarr,
        qbittorrent = qbittorrent,
    )
}
