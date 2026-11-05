package dev.lazier.pub.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Covers, decoded to a size the screen can use.
 *
 * Memory first, then a disk cache, then the server. The grid asks for 320 px
 * thumbnails, so a wall of covers costs a few megabytes rather than tens.
 */
object CoverStore {

    private lateinit var directory: File

    private val memory = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun init(context: Context) {
        directory = File(context.cacheDir, "covers").apply { mkdirs() }
    }

    fun load(bookId: String, width: Int): Bitmap? {
        val key = "$bookId@$width"
        memory.get(key)?.let { return it }

        val file = File(directory, "$bookId-$width.img")
        val bytes = if (file.length() > 0) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            runCatching { Api.cover(bookId, width) }
                .getOrNull()
                ?.also { bytes -> runCatching { file.writeBytes(bytes) } }
        } ?: return null

        val bitmap = decode(bytes, width) ?: return null
        memory.put(key, bitmap)
        return bitmap
    }

    fun forget() {
        memory.evictAll()
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    private fun decode(bytes: ByteArray, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) > width * 2) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
