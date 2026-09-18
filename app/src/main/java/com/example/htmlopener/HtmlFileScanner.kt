package com.example.htmlopener

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import java.io.File
import java.util.Collections
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

    fun scanInternal(): List<HtmlFileEntry> =
        scanDirectories(listOf(Environment.getExternalStorageDirectory()))

    fun scanExternal(): List<HtmlFileEntry> {
        val roots = ArrayList<File>()
        val seenRoots = HashSet<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val storageManager = context.getSystemService(StorageManager::class.java)
                storageManager?.storageVolumes?.forEach { volume ->
                    if (!cancelled.get() &&
                        !volume.isPrimary &&
                        volume.state == Environment.MEDIA_MOUNTED
                    ) {
                        val directory = volume.directory
                        if (directory != null && directory.isDirectory && directory.canRead()) {
                            val path = try { directory.canonicalPath } catch (_: Exception) { directory.absolutePath }
                            if (seenRoots.add(path)) roots.add(directory)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val storage = File("/storage")
        try {
            storage.listFiles()?.forEach { root ->
                if (!cancelled.get() && root.isDirectory &&
                    root.name != "emulated" &&
                    root.name != "self" &&
                    root.canRead()
                ) {
                    val path = try { root.canonicalPath } catch (_: Exception) { root.absolutePath }
                    if (seenRoots.add(path)) roots.add(root)
                }
            }
        } catch (_: SecurityException) {
        }

        val results = scanDirectories(roots).toMutableList()
        val seen = results.mapTo(HashSet()) { it.uri.toString() }
        scanMediaStoreExternal(results, seen)

        return results.sorted()
    }

    fun scan(): List<HtmlFileEntry> {
        val internal = scanInternal()
        if (internal.isNotEmpty()) return internal

        val results = ArrayList<HtmlFileEntry>()
        val seen = HashSet<String>()
        scanMediaStore(results, seen)
        return results.sorted()
    }

    private fun scanDirectories(roots: List<File>): List<HtmlFileEntry> {
        cancelled.set(false)
        val results = ArrayList<HtmlFileEntry>()
        val seenFiles = HashSet<String>()
        val visitedDirectories = HashSet<String>()

        for (root in roots) {
            if (cancelled.get()) break
            scanDirectory(root, results, seenFiles, visitedDirectories)
        }

        return results
            .sortedWith(
                compareBy<HtmlFileEntry> { it.name.lowercase(Locale.ROOT) }
                    .thenBy { it.path.lowercase(Locale.ROOT) }
            )
    }

    private fun scanDirectory(
        directory: File,
        results: MutableList<HtmlFileEntry>,
        seenFiles: MutableSet<String>,
        visitedDirectories: MutableSet<String>
    ) {
        if (cancelled.get() || !directory.isDirectory || !directory.canRead()) return

        val canonical = try {
            directory.canonicalPath
        } catch (_: Exception) {
            directory.absolutePath
        }

        if (!visitedDirectories.add(canonical)) return

        val children = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return

        for (file in children) {
            if (cancelled.get()) return

            if (file.isDirectory) {
                scanDirectory(file, results, seenFiles, visitedDirectories)
            } else if (file.isFile && isHtml(file.name)) {
                val path = try {
                    file.canonicalPath
                } catch (_: Exception) {
                    file.absolutePath
                }

                if (seenFiles.add(path)) {
                    results.add(
                        HtmlFileEntry(
                            name = file.name,
                            path = path,
                            uri = Uri.fromFile(file)
                        )
                    )
                }
            }
        }
    }

    private fun scanMediaStoreExternal(
        results: MutableList<HtmlFileEntry>,
        seen: MutableSet<String>
    ) {
        val volumes = try {
            MediaStore.getExternalVolumeNames(context)
        } catch (_: Exception) {
            emptySet()
        }

        for (volume in volumes) {
            if (cancelled.get() || volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) continue

            val collection = MediaStore.Files.getContentUri(volume)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )

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

                    while (cursor.moveToNext() && !cancelled.get()) {
                        if (idIndex < 0 || nameIndex < 0) continue

                        val name = cursor.getString(nameIndex) ?: continue
                        if (!isHtml(name)) continue

                        val relative = if (relativeIndex >= 0) {
                            cursor.getString(relativeIndex).orEmpty()
                        } else {
                            ""
                        }

                        val path = "/storage/$volume/$relative$name"
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))

                        if (seen.add(uri.toString())) {
                            results.add(HtmlFileEntry(name, path, uri))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun scanMediaStore(
        results: MutableList<HtmlFileEntry>,
        seen: MutableSet<String>
    ) {
        val volumes = try {
            MediaStore.getExternalVolumeNames(context)
        } catch (_: Exception) {
            emptySet()
        }

        for (volume in volumes) {
            if (cancelled.get()) break

            val collection = MediaStore.Files.getContentUri(volume)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )

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

                    while (cursor.moveToNext() && !cancelled.get()) {
                        if (idIndex < 0 || nameIndex < 0) continue

                        val name = cursor.getString(nameIndex) ?: continue
                        if (!isHtml(name)) continue

                        val relative = if (relativeIndex >= 0) {
                            cursor.getString(relativeIndex).orEmpty()
                        } else {
                            ""
                        }

                        val path = if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) {
                            "/storage/emulated/0/$relative$name"
                        } else {
                            "/storage/$volume/$relative$name"
                        }

                        val uri = ContentUris.withAppendedId(
                            collection,
                            cursor.getLong(idIndex)
                        )

                        if (seen.add(uri.toString())) {
                            results.add(HtmlFileEntry(name, path, uri))
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue with the next storage volume.
            }
        }
    }

    private fun isHtml(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".html") || lower.endsWith(".htm")
    }

    private fun List<HtmlFileEntry>.sorted(): List<HtmlFileEntry> =
        this.sortedWith(
            compareBy<HtmlFileEntry> { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.path.lowercase(Locale.ROOT) }
        )
}
