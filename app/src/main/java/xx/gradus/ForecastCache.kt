package xx.gradus

import android.content.Context
import java.io.File

/**
 * The last forecast fetched for each selection, as the service sent it, so the
 * app opens on it at once and still has something to show without a network.
 * One file per selection key: the first line is when it was fetched, the rest
 * the response.
 */
object ForecastCache {
    private const val DIR = "forecasts"

    data class Entry(val json: String, val fetchedAt: Long)

    fun load(context: Context, key: String): Entry? {
        val file = file(context, key)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val newline = text.indexOf('\n')
        if (newline <= 0) return null
        val fetchedAt = text.substring(0, newline).toLongOrNull() ?: return null
        return Entry(text.substring(newline + 1), fetchedAt)
    }

    /** Written beside and renamed over, so a crash mid-write leaves the old copy whole. */
    fun save(context: Context, key: String, entry: Entry) {
        val file = file(context, key)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText("${entry.fetchedAt}\n${entry.json}")
        if (!temp.renameTo(file)) {
            temp.delete()
        }
    }

    fun delete(context: Context, key: String) {
        file(context, key).delete()
    }

    /** "46.4825,30.7233" -> "46_4825_30_7233": a key as a file name. */
    private fun file(context: Context, key: String) =
        File(File(context.filesDir, DIR), key.replace(Regex("[^A-Za-z0-9-]"), "_"))
}
