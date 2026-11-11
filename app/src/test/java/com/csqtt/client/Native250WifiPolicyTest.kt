package com.csqtt.client

import com.csqtt.client.ui.Native250WifiNetworks
import org.junit.Assert.*
import org.junit.Test

class Native250WifiPolicyTest {
    @Test fun savedUiSelectionsDriveAllThreeRules() {
        val networks = Native250WifiNetworks().remember("Home", "blacklist").remember("Other", "all")
        val values = mapOf("wifi.enabled" to "true", "wifi.networks" to networks.encode())
        fun pause(rule: String, ssid: String?, connected: Boolean = true) = native250ShouldPauseForWifi(
            Native250RuntimeConfig(values + ("wifi.rule" to rule)), connected, ssid)
        assertTrue(pause("all", null))
        assertTrue(pause("blacklist", "Home"))
        assertFalse(pause("blacklist", "home"))
        assertFalse(pause("blacklist", "Other"))
        assertFalse(pause("blacklist", null))
        assertFalse(pause("whitelist", "Home"))
        assertTrue(pause("whitelist", "Other"))
        assertTrue(pause("whitelist", null))
        assertFalse(pause("all", "Home", false))
        assertFalse(native250ShouldPauseForWifi(Native250RuntimeConfig(values + ("wifi.enabled" to "false")), true, "Home"))
        val removed = values + ("wifi.networks" to networks.forget("Home").encode()) + ("wifi.rule" to "blacklist")
        assertFalse(native250ShouldPauseForWifi(Native250RuntimeConfig(removed), true, "Home"))
    }
}
