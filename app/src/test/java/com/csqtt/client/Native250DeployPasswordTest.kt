package com.csqtt.client

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class Native250DeployPasswordTest {
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
    @Test fun successfulDeployUpdatesCapturedLinkedProfileAfterUserSwitchesTabsOrProfiles() = runTest {
        val data = MemoryStore(preferencesOf(
            intPreferencesKey("active_profile") to 0,
            intPreferencesKey("deploy_connection_profile") to 1,
            stringPreferencesKey("connection_password_local_1") to "original-one",
            stringPreferencesKey("connection_password_local_2") to "original-two",
        ))
        val store = store(data)
        val target = store.deployConnectionProfile(0)
        store.saveActiveProfile(2)
        store.saveConnectionPassword("new-owner-password", target)
        assertEquals("original-two", store.connectionPassword.first())
        store.saveActiveProfile(1)
        assertEquals("new-owner-password", store.connectionPassword.first())
        assertEquals(1, data.data.value[intPreferencesKey("deploy_connection_profile")])
    }
    @Test fun unlinkedOriginalProfileStaysItsOwnTargetAndInvalidLinkCannotOverwriteAnother() = runTest {
        assertEquals(3, store(MemoryStore(emptyPreferences())).deployConnectionProfile(3))
        val data = MemoryStore(preferencesOf(intPreferencesKey("deploy_connection_profile") to 31))
        try { store(data).deployConnectionProfile(0); fail("Must reject an inaccessible target before deployment") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, data.data.value.asMap().size)
    }
}
