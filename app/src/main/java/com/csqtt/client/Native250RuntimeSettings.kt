package com.csqtt.client

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The accepted UI and services share the same persisted choices, including existing preview choices. */
internal data class Native250RuntimeConfig(val values: Map<String, String>) {
    fun value(key: String, default: String = ""): String = values[key] ?: default
    fun flag(key: String): Boolean = value(key).toBooleanStrictOrNull() ?: false
}

internal class Native250RuntimeSettings(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    fun put(key: String, value: String) { preferences.edit().putString(key, value).apply() }

    fun migrateLegacy(values: Map<String, Any?>) = synchronized(preferences) {
        if (!preferences.contains("migration.59")) {
            val edit = preferences.edit()
            native250LegacySettings(values).forEach { (key, value) ->
                if (!preferences.contains(key)) edit.putString(key, value)
            }
            check(edit.putString("migration.59", "done").commit()) { "Cannot persist settings migration" }
        }
    }

    fun snapshot() = Native250RuntimeConfig(preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap())

    val changes: Flow<Native250RuntimeConfig> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        const val PREFERENCES = "native250_ui_preview"
    }
}
