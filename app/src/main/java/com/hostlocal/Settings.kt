package com.hostlocal

import android.content.Context

/** Persisted user config. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("hostlocal", Context.MODE_PRIVATE)

    var folderPath: String
        get() = prefs.getString(KEY_FOLDER, DEFAULT_FOLDER) ?: DEFAULT_FOLDER
        set(value) = prefs.edit().putString(KEY_FOLDER, value).apply()

    companion object {
        const val DEFAULT_FOLDER = "/storage/emulated/0/Movies"
        const val FTP_PORT = 2121
        const val PASSIVE_PORTS = "50000-50010"
        private const val KEY_FOLDER = "folder"
    }
}
