package com.csqtt.client

import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class Native250RuntimeSettingsTest {
    @Test fun migrationPreservesExistingUiChoicesAndRunsOnlyOnce() {
        val prefs = mock(SharedPreferences::class.java)
        val edit = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenReturn(edit)
        `when`(edit.putString(anyString(), anyString())).thenReturn(edit)
        `when`(edit.commit()).thenReturn(true)
        `when`(prefs.contains("gooby.1")).thenReturn(true)
        val runtime = Native250RuntimeSettings(prefs)
        runtime.migrateLegacy(mapOf("gooby_enabled_1" to true))
        verify(edit, never()).putString(eq("gooby.1"), anyString())
        verify(edit).putString("migration.59", "done")
        verify(edit).commit()
        clearInvocations(edit)
        `when`(prefs.contains("migration.59")).thenReturn(true)
        runtime.migrateLegacy(emptyMap())
        verifyNoInteractions(edit)
    }

    @Test fun servicesReceiveSavedValuesAndChangesAndReleaseListener() = runTest {
        val prefs = mock(SharedPreferences::class.java)
        var data: Map<String, *> = mapOf("gooby.0" to "gentle", "unrelated" to 12)
        `when`(prefs.all).thenAnswer { data }
        var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
        doAnswer { listener = it.getArgument(0); null }.`when`(prefs)
            .registerOnSharedPreferenceChangeListener(any())
        val values = mutableListOf<Native250RuntimeConfig>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            Native250RuntimeSettings(prefs).changes.take(2).toList(values)
        }
        assertEquals("gentle", values.single().value("gooby.0"))
        assertFalse(values.single().values.containsKey("unrelated"))
        data = mapOf("gooby.0" to "extended", "gooby.1" to "off")
        listener!!.onSharedPreferenceChanged(prefs, "gooby.0")
        job.join()
        assertEquals("extended", values.last().value("gooby.0"))
        assertEquals("off", values.last().value("gooby.1"))
        verify(prefs).unregisterOnSharedPreferenceChangeListener(listener)
    }
}
