package com.csqtt.client

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OperationProfileDataStoreTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(data.value).also { data.value = it }
    }

    @Test fun delayedDeployCompletionKeepsOriginalProfileAndBothNewSelections() = runTest {
        val base = MemoryStore()
        val vpn = intPreferencesKey("active_profile")
        val vps = intPreferencesKey("active_deploy_profile")
        base.edit { it[vpn] = 1; it[vps] = 4 }
        val operation = OperationProfileDataStore(base, 4)
        val completed = CompletableDeferred<Unit>()
        val result = async {
            completed.await()
            operation.edit {
                val profile = it[vpn]!!
                it[booleanPreferencesKey("deploy_server_configured_$profile")] = true
                it[booleanPreferencesKey("docker_install_$profile")] = true
                it[stringPreferencesKey("deploy_last_inspection_version_$profile")] = "2.5.3"
            }
        }
        base.edit { it[vpn] = 2; it[vps] = 6 }
        completed.complete(Unit)
        result.await()
        val prefs = base.data.first()
        assertEquals(2, prefs[vpn])
        assertEquals(6, prefs[vps])
        assertEquals(true, prefs[booleanPreferencesKey("deploy_server_configured_4")])
        assertEquals(true, prefs[booleanPreferencesKey("docker_install_4")])
        assertEquals("2.5.3", prefs[stringPreferencesKey("deploy_last_inspection_version_4")])
        assertNull(prefs[booleanPreferencesKey("deploy_server_configured_6")])
        assertNull(prefs[booleanPreferencesKey("docker_install_6")])
        assertEquals(4, operation.data.first()[vpn])
    }
}
