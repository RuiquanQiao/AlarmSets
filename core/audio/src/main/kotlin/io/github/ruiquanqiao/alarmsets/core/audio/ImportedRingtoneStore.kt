package io.github.ruiquanqiao.alarmsets.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** One piece of audio the user imported, as recorded in the index file. */
@Serializable
data class ImportedEntry(
    val id: String,
    val fileName: String,
    val displayName: String,
    val durationMillis: Long,
)

@Serializable
private data class ImportedIndex(val entries: List<ImportedEntry> = emptyList())

/**
 * Owns the audio the user imported.
 *
 * Imported files are **copied into app storage** rather than referenced in
 * place. A `content://` URI handed over by the system file picker is a
 * temporary grant: it can be revoked, and the file behind it can be moved,
 * renamed or deleted by whatever app owns it. An alarm that silently stops
 * having a sound six weeks later is the worst kind of bug in this app, so the
 * cost of a duplicate copy is worth paying.
 *
 * There is no duration limit. A three-second chime and a six-minute track are
 * handled identically.
 */
class ImportedRingtoneStore(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private val indexFile: File
        get() = File(directory, INDEX_FILE)

    suspend fun list(): List<ImportedEntry> = withContext(io) { readIndex().entries }

    suspend fun find(id: String): ImportedEntry? = withContext(io) {
        readIndex().entries.firstOrNull { it.id == id }
    }

    /**
     * Synchronous lookup for the player, which is driven from a foreground
     * service and must start a tone without first suspending. Reads one small
     * JSON file.
     */
    fun findBlocking(id: String): ImportedEntry? =
        readIndex().entries.firstOrNull { it.id == id }

    fun fileFor(entry: ImportedEntry): File = File(directory, entry.fileName)

    suspend fun import(sourceUri: String, fallbackName: String): Result<ImportedEntry> =
        withContext(io) {
            runCatching {
                val uri = Uri.parse(sourceUri)
                val resolver = context.contentResolver

                val displayName = queryDisplayName(uri) ?: fallbackName
                val extension = displayName.substringAfterLast('.', "").lowercase()
                    .takeIf { it.isNotEmpty() && it.length <= 5 }
                    ?: "audio"

                val id = UUID.randomUUID().toString()
                val target = File(directory, "$id.$extension")

                resolver.openInputStream(uri).use { input ->
                    checkNotNull(input) { "cannot open $sourceUri" }
                    target.outputStream().use { output -> input.copyTo(output) }
                }

                val entry = ImportedEntry(
                    id = id,
                    fileName = target.name,
                    displayName = displayName.substringBeforeLast('.').ifBlank { fallbackName },
                    durationMillis = readDuration(target),
                )
                writeIndex(ImportedIndex(readIndex().entries + entry))
                entry
            }.onFailure {
                // Never leave a half-copied file behind.
                directory.listFiles()?.forEach { file ->
                    if (file.name != INDEX_FILE && file.length() == 0L) file.delete()
                }
            }
        }

    suspend fun delete(id: String) = withContext(io) {
        val index = readIndex()
        index.entries.firstOrNull { it.id == id }?.let { entry ->
            File(directory, entry.fileName).delete()
        }
        writeIndex(ImportedIndex(index.entries.filterNot { it.id == id }))
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()

    private fun readDuration(file: File): Long = runCatching {
        // MediaMetadataRetriever only became AutoCloseable in API 29, and this
        // module supports 26, so it is released by hand.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    private fun readIndex(): ImportedIndex =
        runCatching {
            if (!indexFile.exists()) return ImportedIndex()
            json.decodeFromString(ImportedIndex.serializer(), indexFile.readText())
        }.getOrDefault(ImportedIndex())

    private fun writeIndex(index: ImportedIndex) {
        indexFile.writeText(json.encodeToString(ImportedIndex.serializer(), index))
    }

    private companion object {
        const val DIRECTORY = "imported_ringtones"
        const val INDEX_FILE = "index.json"
    }
}
