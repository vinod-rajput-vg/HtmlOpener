package com.example.htmlopener

import android.net.Uri
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

data class HtmlFileEntry(
    val file: File,
    val uri: Uri
)

class HtmlFileScanner {

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun scan(): List<HtmlFileEntry> {
        cancelled.set(false)

        val roots = findStorageRoots()
        val results = ArrayList<HtmlFileEntry>()
        val seen = HashSet<String>()

        for (root in roots) {
            if (cancelled.get()) break
            scanDirectory(root, results, seen)
        }

        return results.sortedWith(
            compareBy<HtmlFileEntry> { it.file.name.lowercase() }
                .thenBy { it.file.absolutePath.lowercase() }
        )
    }

    private fun scanDirectory(
        directory: File,
        results: MutableList<HtmlFileEntry>,
        seen: MutableSet<String>
    ) {
        if (cancelled.get() || !directory.isDirectory || !directory.canRead()) return

        val canonicalPath = try {
            directory.canonicalPath
        } catch (_: Exception) {
            directory.absolutePath
        }

        if (!seen.add(canonicalPath)) return

        val children = try {
            directory.listFiles()
        } catch (_: Exception) {
            null
        } ?: return

        for (child in children) {
            if (cancelled.get()) return

            try {
                if (child.isDirectory) {
                    if (!child.isHidden) {
                        scanDirectory(child, results, seen)
                    }
                } else if (child.isFile && isHtmlFile(child.name)) {
                    results.add(HtmlFileEntry(child, Uri.fromFile(child)))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun findStorageRoots(): List<File> {
        val roots = ArrayList<File>()
        val storage = File("/storage")

        try {
            storage.listFiles()?.forEach { root ->
                if (root.isDirectory && root.canRead() && root.name != "emulated") {
                    roots.add(root)
                }
            }
        } catch (_: Exception) {
        }

        val internal = File("/storage/emulated/0")
        if (internal.isDirectory && internal.canRead()) {
            roots.add(0, internal)
        }

        return roots.distinctBy {
            try {
                it.canonicalPath
            } catch (_: Exception) {
                it.absolutePath
            }
        }
    }

    private fun isHtmlFile(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return lower.endsWith(".html") || lower.endsWith(".htm")
    }
}
