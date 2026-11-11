package com.csqtt.client

import org.junit.Assert.*
import org.junit.Test

class GoobyPolicyTest {
    @Test fun acceptedDropdownMapsToRuntimeAndKeepsProfilesSeparate() {
        val config = Native250RuntimeConfig(mapOf("gooby.0" to "gentle", "gooby.1" to "off", "gooby.2" to "extended"))
        assertEquals(true to "gentle", GoobyPolicy.fromUi(config, 0))
        assertFalse(GoobyPolicy.fromUi(config, 1).first)
        assertEquals(true to "extended", GoobyPolicy.fromUi(config, 2))
        assertFalse(GoobyPolicy.fromUi(config, 3).first)
        assertFalse(GoobyPolicy.fromUi(Native250RuntimeConfig(mapOf("gooby.0" to "invalid")), 0).first)
    }
    @Test fun presetsAreProfileValuesAndNeverAffectDirectOrUdpArguments() {
        for ((strategy, _) in GoobyPolicy.strategies) {
            assertTrue(GoobyPolicy.arguments(true, "udp", strategy).isEmpty())
            assertTrue(GoobyPolicy.arguments(false, CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS, strategy).isEmpty())
        }
        assertEquals(listOf("--gooby", "--gooby-strategy", "gentle"), GoobyPolicy.arguments(true, CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS, "gentle"))
        assertEquals(listOf("--gooby", "--gooby-strategy", "extended"), GoobyPolicy.arguments(true, CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS, "extended"))
        assertEquals("current", GoobyPolicy.normalizeStrategy("future-unknown"))
        assertEquals("current", GoobyPolicy.normalizeStrategy(null))
    }
    @Test fun goobyNeverChangesUdpOrDisabledConnections() {
        assertTrue(GoobyPolicy.arguments(false, CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS).isEmpty())
        assertTrue(GoobyPolicy.arguments(true, "udp").isEmpty())
        assertTrue(GoobyPolicy.arguments(false, "udp").isEmpty())
        assertTrue(GoobyPolicy.arguments(true, "unknown").isEmpty())
        assertEquals(listOf("--gooby"), GoobyPolicy.arguments(true, CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS))
        assertFalse(TunnelParams(peer = "", vkHashes = "", workersPerHash = 1, port = 0).goobyEnabled)
    }
}
