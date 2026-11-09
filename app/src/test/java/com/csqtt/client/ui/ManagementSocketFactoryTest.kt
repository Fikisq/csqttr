package com.csqtt.client.ui.legacy

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test

class ManagementSocketFactoryTest {
    @Test fun protectionRunsBeforeConnection() {
        ServerSocket(0).use { server ->
            var protected = false
            val factory = ManagementSocketFactory { socket ->
                assertTrue("Descriptor must exist before Android VPN protection", socket.isBound)
                assertFalse(socket.isConnected)
                protected = true
                true
            }
            factory.createSocket("127.0.0.1", server.localPort).use {
                assertTrue(protected)
                assertTrue(it.isConnected)
            }
        }
    }

    @Test fun failedProtectionClosesSocketInsteadOfUsingVpn() {
        var attempted: Socket? = null
        val factory = ManagementSocketFactory { attempted = it; false }
        assertThrows(IOException::class.java) { factory.createSocket() }
        assertTrue(attempted!!.isClosed)
        assertFalse(attempted!!.isConnected)
    }
}
