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
