package com.example.htmlopener

import android.content.Context
import android.net.Uri

class SettingsManager(context: Context) {
    private val preferences = context.getSharedPreferences(
        "html_opener_settings",
        Context.MODE_PRIVATE
    )

    fun getDefaultBrowser(): String? =
        preferences.getString(KEY_DEFAULT_BROWSER, null)

    fun setDefaultBrowser(packageName: String) {
        preferences.edit().putString(KEY_DEFAULT_BROWSER, packageName).apply()
    }

    fun getStorageRoot(): Uri? =
        preferences.getString(KEY_STORAGE_ROOT, null)?.let(Uri::parse)

    fun setStorageRoot(uri: Uri) {
        preferences.edit().putString(KEY_STORAGE_ROOT, uri.toString()).apply()
    }

    companion object {
        private const val KEY_DEFAULT_BROWSER = "default_browser"
        private const val KEY_STORAGE_ROOT = "storage_root"
    }
}
