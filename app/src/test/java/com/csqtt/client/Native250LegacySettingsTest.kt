package com.csqtt.client

import com.csqtt.client.ui.Native250WifiNetworks
import org.junit.Assert.*
import org.junit.Test

class Native250LegacySettingsTest {
    @Test fun copiesProfileChoicesAndWifiListsWithoutCopyingSecrets() {
        val legacy = mapOf<String, Any?>("gooby_enabled_1" to true, "gooby_strategy_1" to "extended",
            "extra_workers_1" to true, "battery_saver_mode" to "level", "battery_saver_percent" to 25,
            "auto_pause_on_wifi" to true, "wifi_network_list_ssids" to "Home", "wifi_saved_ssids" to "Other",
            "deploy_password_encrypted" to "private-test-placeholder")
        val result = native250LegacySettings(legacy)
        assertEquals("off", result["gooby.0"])
        assertEquals("extended", result["gooby.1"])
        assertEquals("true", result["extraWorkers.1"])
        assertEquals("25", result["battery.percent"])
        assertEquals("level", result["battery.mode"])
        assertEquals("whitelist", result["wifi.rule"])
        val wifi = Native250WifiNetworks.decode(result.getValue("wifi.networks"))
        assertEquals(setOf("Home", "Other"), wifi.saved)
        assertEquals(setOf("Home"), wifi.selected)
        assertFalse(result.values.contains("private-test-placeholder"))
        assertEquals("private-test-placeholder", legacy["deploy_password_encrypted"])
    }
    @Test fun newInstallKeepsAcceptedDefaults() {
        val result = native250LegacySettings(emptyMap())
        assertEquals("false", result["wifi.enabled"])
        assertEquals("all", result["wifi.rule"])
        assertEquals("auto", result["battery.mode"])
        assertEquals("30", result["battery.percent"])
        assertEquals("false", result["extraWorkers.0"])
    }
}
