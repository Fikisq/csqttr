package com.csqtt.client

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class Native250AppRoutingStoreTest {
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    private fun store(data: MemoryStore): SettingsStore {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        return SettingsStore(context, data)
    }

    @Test fun savingCapturedProfilePreservesOthersAndSelectionOrder() = runTest {
        val data = MemoryStore(preferencesOf(intPreferencesKey("active_profile") to 1,
            stringPreferencesKey("excluded_apps_1") to "keep.owner"))
        val store = store(data)
        store.saveAppRoutingSelection("z.app,a.app", "w.app", false, setOf("black"), 0, false)
        assertEquals("keep.owner", store.excludedApps.first())
        store.saveActiveProfile(0)
        assertEquals("z.app,a.app", store.excludedApps.first())
        assertEquals("w.app", store.whitelistApps.first())
        assertEquals(setOf("black"), store.appPresetsUsed.first())
        assertEquals(false, store.appRoutingAll.first())
        assertFalse(store.isWhitelist.first())
    }

    @Test fun allModeKeepsBothListsAndDeliberateClearStaysEmpty() = runTest {
        val store = store(MemoryStore(emptyPreferences()))
        store.saveAppRoutingSelection("custom.direct", "custom.vpn", false, setOf("black", "white"), 0, true)
        assertEquals(true, store.appRoutingAll.first())
        assertEquals("custom.direct", store.excludedApps.first())
        assertEquals("custom.vpn", store.whitelistApps.first())
        store.saveAppRoutingSelection("", "custom.vpn", false, store.appPresetsUsed.first(), 0, false)
        assertTrue(store.excludedApps.first().isEmpty())
        assertEquals("custom.vpn", store.whitelistApps.first())
        assertTrue(AppRoutingPresets.initial(emptySet(), "black" in store.appPresetsUsed.first(),
            AppRoutingPresets.direct, false).isEmpty())
    }

    @Test fun readsExisting59SettingsAndDoesNotInventAllModeForLegacyProfiles() = runTest {
        val store = store(MemoryStore(preferencesOf(intPreferencesKey("active_profile") to 2,
            booleanPreferencesKey("app_routing_all_2") to true,
            stringPreferencesKey("app_presets_used_2") to "white,black")))
        assertEquals(true, store.appRoutingAll.first())
        assertEquals(setOf("white", "black"), store.appPresetsUsed.first())
        store.saveActiveProfile(0)
        assertNull(store.appRoutingAll.first())
    }
}
