package com.csqtt.client

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.map

/** Pins asynchronous operation writes without changing either UI selection. */
internal class OperationProfileDataStore(
    private val delegate: DataStore<Preferences>,
    private val profile: Int,
) : DataStore<Preferences> {
    init { require(profile in CsqttConstants.Profiles.MIN_INDEX..CsqttConstants.Profiles.MAX_INDEX) }
    private val cursor = intPreferencesKey("active_profile")
    private val deployCursor = intPreferencesKey("active_deploy_profile")
    private fun scoped(prefs: Preferences) = prefs.toMutablePreferences().apply {
        this[cursor] = profile
    }.toPreferences()
    override val data = delegate.data.map(::scoped)
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val result = delegate.updateData { original ->
            transform(scoped(original)).toMutablePreferences().apply {
                for (key in listOf(cursor, deployCursor)) {
                    original[key]?.let { this[key] = it } ?: remove(key)
                }
            }.toPreferences()
        }
        return scoped(result)
    }
}
