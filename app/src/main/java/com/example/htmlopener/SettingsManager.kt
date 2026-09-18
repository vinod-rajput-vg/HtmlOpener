package com.example.htmlopener

import android.content.Context

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

    companion object {
        private const val KEY_DEFAULT_BROWSER = "default_browser"
    }
}
