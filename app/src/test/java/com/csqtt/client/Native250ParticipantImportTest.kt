package com.csqtt.client

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.csqtt.client.ui.utils.formatCsqttLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class Native250ParticipantImportTest {
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    private fun store(data: DataStore<Preferences>): SettingsStore {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        return SettingsStore(context, data)
    }
    private val link = formatCsqttLink("203.0.113.7", 46123, "ExamplePass123456", listOf("abcdefghijklmnop1"))

    @Test fun incomingLinkAddsParticipantAndPreservesOwnerAndDeployProfiles() = runTest {
        val data = MemoryStore(preferencesOf(
            intPreferencesKey("active_profile") to 1,
            stringPreferencesKey("deploy_ip") to "owner.example",
            stringPreferencesKey("peer_1") to "existing.example",
            stringPreferencesKey("connection_password_local_1") to "owner-password",
        ))
        val store = store(data)
        assertEquals(2, store.importCsqttParticipantProfile(link))
        assertEquals(2, store.activeProfile.first())
        assertTrue(store.csqttLinkMode.first())
        assertEquals("203.0.113.7", store.peer.first())
        assertEquals(46123, store.serverPeerPort.first())
        assertEquals("ExamplePass123456", store.connectionPassword.first())
        assertEquals("abcdefghijklmnop1", store.vkHashes.first())
        assertEquals("owner.example", data.data.value[stringPreferencesKey("deploy_ip")])
        assertEquals("owner-password", data.data.value[stringPreferencesKey("connection_password_local_1")])
        val before = data.data.value.asMap()
        assertEquals(2, store.importCsqttParticipantProfile(link))
        assertEquals(before, data.data.value.asMap())
    }
    @Test fun activeConnectionAndFullProfileListAreProtected() = runTest {
        val data = MemoryStore(preferencesOf(intPreferencesKey("active_profile") to 1,
            stringPreferencesKey("peer_1") to "existing.example"))
        val store = store(data)
        assertEquals(0, store.importCsqttParticipantProfile(link, activate = false))
        assertEquals(1, store.activeProfile.first())
        val full = MemoryStore(preferencesOf(
            stringPreferencesKey("peer") to "one", stringPreferencesKey("deploy_ip_1") to "two",
            stringPreferencesKey("connection_password_local_2") to "reserved", stringPreferencesKey("profile_name_3") to "reserved-name"))
        val before = full.data.value.asMap()
        try { store(full).importCsqttParticipantProfile(link); fail("Must not replace an existing profile") }
        catch (_: IllegalStateException) { }
        assertEquals(before, full.data.value.asMap())
        try { store.importCsqttParticipantProfile("csqtt://invalid"); fail("Must reject invalid links") }
        catch (_: IllegalArgumentException) { }
    }
}
