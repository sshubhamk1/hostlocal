package com.hostlocal

import android.content.Context

/** Persisted user config. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("hostlocal", Context.MODE_PRIVATE)

    var folderPath: String
        get() = prefs.getString(KEY_FOLDER, DEFAULT_FOLDER) ?: DEFAULT_FOLDER
        set(value) = prefs.edit().putString(KEY_FOLDER, value).apply()

    /** Stable DLNA device id so clients don't see a new server every launch. */
    val dlnaUuid: String
        get() = prefs.getString(KEY_UUID, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_UUID, it).apply()
        }

    companion object {
        const val DLNA_PORT = 8200
        const val DLNA_NAME = "HostLocal"
        private const val KEY_UUID = "dlna_uuid"
        const val DEFAULT_FOLDER = "/storage/emulated/0/Movies"
        const val FTP_PORT = 2121
        const val PASSIVE_PORTS = "50000-50010"
        private const val KEY_FOLDER = "folder"
    }
}
