package dev.lazier.pub.reader

import android.content.Context
import dev.lazier.pub.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Downloads a book once and keeps it, so the reader works offline afterwards. */
object ReaderFiles {

    suspend fun prepare(context: Context, bookId: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "books").apply { mkdirs() }
            val target = File(directory, "$bookId.epub")
            if (target.length() <= 0) {
                Api.download(bookId, target) { copied, total ->
                    onProgress(if (total > 0) copied.toFloat() / total else 0f)
                }
            }
            target
        }
}
