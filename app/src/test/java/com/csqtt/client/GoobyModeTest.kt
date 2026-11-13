package com.csqtt.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoobyModeTest {
    @Test
    fun udpAndOffNeverSendGoobyArguments() {
        GoobyMode.entries.forEach { mode ->
            assertTrue(mode.arguments(CsqttConstants.Tunnel.DEFAULT_TURN_TRANSPORT).isEmpty())
        }
        assertTrue(GoobyMode.OFF.arguments(CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS).isEmpty())
    }

    @Test
    fun classicPresetsKeepTheirNativeStrategyKeys() {
        val tcp = CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
        assertEquals(listOf("--gooby", "--gooby-strategy", "gentle"), GoobyMode.GENTLE.arguments(tcp))
        assertEquals(listOf("--gooby", "--gooby-strategy", "current"), GoobyMode.CURRENT.arguments(tcp))
        assertEquals(listOf("--gooby", "--gooby-strategy", "extended"), GoobyMode.EXTENDED.arguments(tcp))
    }

    @Test
    fun previouslySavedEnableFlagControlsLegacyStrategy() {
        assertEquals(GoobyMode.OFF, GoobyMode.fromLegacy(false, "extended"))
        assertEquals(GoobyMode.EXTENDED, GoobyMode.fromLegacy(true, "extended"))
        assertEquals(GoobyMode.CURRENT, GoobyMode.fromLegacy(true, null))
        assertEquals(GoobyMode.OFF, GoobyMode.fromStored(null))
    }
}
