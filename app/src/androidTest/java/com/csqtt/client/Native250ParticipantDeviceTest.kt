package com.csqtt.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.csqtt.client.ui.utils.parseCsqttLink
import com.csqtt.client.ui.utils.peerAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run only after the browser or the user has imported a participant link through the UI. */
@RunWith(AndroidJUnit4::class)
class Native250ParticipantDeviceTest {
    @Test fun prepareEmptyProfileForManualImport() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val profile = requireNotNull(args.getString("emptyProfile")).toInt()
        require(profile in 0..3)
        val store = SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.saveActiveProfile(profile)
        assertTrue("Refusing to reuse an occupied profile", store.peer.first().isBlank() &&
            store.csqttLink.first().isBlank() && store.connectionPassword.first().isBlank() &&
            store.deployIp.first().isBlank())
        store.saveCsqttLinkMode(true)
    }

    @Test fun importedLinkMatchesActiveTunnel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(context)
        val auth = store.tunnelAuthSnapshot.first { it.isLoaded }
        assertTrue("Active profile is not a participant", auth.csqttLinkMode)
        val parsed = requireNotNull(parseCsqttLink(auth.csqttLink)) { "Imported link cannot be parsed" }
        val source = requireNotNull(resolveConnectionSource(store))
        assertTrue("Effective server or port mismatch", source.peer == parsed.peerAddress())
        assertTrue("Effective password mismatch", source.password == parsed.password)
        assertTrue("Effective hashes mismatch", source.hashes.split(',').filter(String::isNotBlank) == parsed.hashes)
        assertTrue("Effective token mismatch", source.vkAccessToken == parsed.vkAccessToken)
        // The original manual/QR flow reads the link directly; it does not replace owner fields.
        if (InstrumentationRegistry.getArguments().getString("manualImport") != "true") {
            assertTrue("Server address mismatch", store.peer.first() == parsed.host)
            assertEquals("Peer port mismatch", parsed.port, store.serverPeerPort.first())
            assertTrue("Manual peer port is not enabled", store.manualPortsEnabled.first())
            assertTrue("Password mismatch", auth.connectionPassword == parsed.password)
            assertTrue("Hashes mismatch", store.vkHashes.first().split(',').filter(String::isNotBlank) == parsed.hashes)
        }
        assertFalse("New participant has extra workers enabled", store.extraWorkers.first())
    }
}
