package com.example.htmlopener

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class HtmlFileEntry(
    val name: String,
    val path: String,
    val uri: Uri
)

class HtmlFileScanner(private val context: Context) {

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun scan(): List<HtmlFileEntry> {
        cancelled.set(false)

        val results = ArrayList<HtmlFileEntry>()
        val seen = HashSet<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumes = try {
                MediaStore.getExternalVolumeNames(context)
            } catch (_: Exception) {
                emptySet()
            }

            for (volume in volumes) {
                if (cancelled.get()) break
                scanVolume(volume, results, seen)
            }
        } else {
            scanLegacy(results, seen)
        }

        return results
            .sortedWith(
                compareBy<HtmlFileEntry> { it.name.lowercase(Locale.ROOT) }
                    .thenBy { it.path.lowercase(Locale.ROOT) }
            )
    }

    private fun scanVolume(
        volume: String,
        results: MutableList<HtmlFileEntry>,
        seen: MutableSet<String>
    ) {
        val collection = MediaStore.Files.getContentUri(volume)

        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            }
            @Suppress("DEPRECATION")
            add(MediaStore.Files.FileColumns.DATA)
        }.toTypedArray()

        /*
         * Do not filter with SQL LIKE here. Some Android TV/OEM MediaStore
         * implementations handle filename matching inconsistently. Reading
         * DISPLAY_NAME and checking the extension in Kotlin is more reliable.
         */
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { cursor ->

                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val relativeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                if (idIndex < 0 || nameIndex < 0) return@use

                while (cursor.moveToNext() && !cancelled.get()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: continue

                    if (!isHtml(name)) continue

                    val relativePath = if (relativeIndex >= 0) {
                        cursor.getString(relativeIndex).orEmpty()
                    } else {
                        ""
                    }

                    @Suppress("DEPRECATION")
                    val dataPath = if (dataIndex >= 0) {
                        cursor.getString(dataIndex).orEmpty()
                    } else {
                        ""
                    }

                    val displayPath = when {
                        dataPath.isNotBlank() -> dataPath
                        volume == MediaStore.VOLUME_EXTERNAL_PRIMARY ->
                            "/storage/emulated/0/" + relativePath + name
                        else ->
                            "/storage/" + volume + "/" + relativePath + name
                    }

                    val uri = ContentUris.withAppendedId(collection, id)

                    if (seen.add(uri.toString())) {
                        results.add(HtmlFileEntry(name, displayPath, uri))
                    }
                }
            }
        } catch (_: Exception) {
            // A storage volume may be unavailable or restricted on some TV ROMs.
        }
    }

    private fun scanLegacy(
        results: MutableList<HtmlFileEntry>,
        seen: MutableSet<String>
    ) {
        val collection = MediaStore.Files.getContentUri("external")

        val displayName = MediaStore.Files.FileColumns.DISPLAY_NAME
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            displayName,
            MediaStore.Files.FileColumns.DATA
        )

        try {
            @Suppress("DEPRECATION")
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                displayName + " COLLATE NOCASE ASC"
            )?.use { cursor ->

                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndex(displayName)
                val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                if (idIndex < 0 || nameIndex < 0) return@use

                while (cursor.moveToNext() && !cancelled.get()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: continue

                    if (!isHtml(name)) continue

                    val path = if (dataIndex >= 0) {
                        cursor.getString(dataIndex).orEmpty()
                    } else {
                        name
                    }

                    val uri = ContentUris.withAppendedId(collection, id)

                    if (seen.add(uri.toString())) {
                        results.add(HtmlFileEntry(name, path, uri))
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore inaccessible storage providers.
        }
    }

    private fun isHtml(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".html") || lower.endsWith(".htm")
    }
}