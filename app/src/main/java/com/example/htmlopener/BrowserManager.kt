package com.example.htmlopener

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

class BrowserManager(private val context: Context) {

    private val packageManager: PackageManager
        get() = context.packageManager

    fun getInstalledBrowsers(): List<BrowserInfo> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("http://example.com")
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

        return resolveInfos.mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            BrowserInfo(
                packageName = activityInfo.packageName,
                label = info.loadLabel(packageManager).toString()
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun getDefaultBrowserPackage(): String? {
        val browsers = getInstalledBrowsers()
        return browsers.firstOrNull()?.packageName
    }

    fun isBrowserInstalled(packageName: String): Boolean =
        try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

    fun openHtmlUri(packageName: String, uri: Uri): Boolean =
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                type = "text/html"
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }

    fun openUrl(packageName: String, url: String): Boolean =
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
}
