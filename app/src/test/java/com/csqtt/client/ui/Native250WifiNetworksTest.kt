package com.csqtt.client.ui

import org.junit.Assert.*
import org.junit.Test

class Native250WifiNetworksTest {
    @Test fun forgetAlsoRemovesTheSavedSelection() {
        val value = Native250WifiNetworks().remember("Test Wi-Fi", "blacklist").remember("Guest", "whitelist")
        val restored = Native250WifiNetworks.decode(value.forget("Test Wi-Fi").encode())
        assertEquals(setOf("Guest"), restored.saved)
        assertEquals(setOf("Guest"), restored.selected)
    }

    @Test fun rememberingOnAnyWifiDoesNotInventAnException() {
        val value = Native250WifiNetworks().remember("Test Wi-Fi", "all")
        assertTrue(value.selected.isEmpty())
        assertEquals(value, value.select("Not saved", true))
        assertEquals(setOf("Test Wi-Fi"), value.select("Test Wi-Fi", true).selected)
    }

    @Test fun invalidStoredDataDoesNotExposeOrphanSelections() {
        assertEquals(Native250WifiNetworks(), Native250WifiNetworks.decode("broken"))
        val value = Native250WifiNetworks.decode("""{"saved":["Test",null,5],"selected":["Test","Missing"]}""")
        assertEquals(setOf("Test"), value.saved)
        assertEquals(setOf("Test"), value.selected)
    }

    @Test fun ssidLimitIsUtf8BytesAndRejectsAndroidUnknownName() {
        assertEquals("Тест", native250Ssid(" \"Тест\" "))
        assertNotNull(native250Ssid("я".repeat(16)))
        assertNull(native250Ssid("я".repeat(17)))
        assertNull(native250Ssid("<unknown ssid>"))
        assertNull(native250Ssid("Test\nGuest"))
        assertNull(native250Ssid(" "))
    }
}
