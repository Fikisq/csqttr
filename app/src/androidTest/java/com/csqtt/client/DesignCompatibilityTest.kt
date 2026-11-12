package com.csqtt.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Switch actual Activity designs without SSH, VPN or VK authentication. */
class DesignCompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun threeDesignsShareSettingsAndPreserveSelectedConnection() {
        val store = SettingsStore(compose.activity)
        val oldDesign = runBlocking { store.uiDesign.first() }
        val oldMode = runBlocking { store.batterySaverMode.first() }
        val oldPercent = runBlocking { store.batterySaverPercent.first() }
        val oldOwner = runBlocking { store.uiOwnerMode.first() }
        val oldShowLogs = runBlocking { store.participantShowLogs.first() }
        val profile = runBlocking { store.activeProfile.first() }
        val connection = runBlocking { store.readProfileConnectionSettings(profile) }
        try {
            runBlocking {
                store.saveUpdateCheckIntervalHours(UPDATE_CHECK_NEVER)
                store.saveUiDesign("v2")
                store.saveUiOwnerMode(false)
                store.saveParticipantShowLogs(true)
                store.saveBatterySaverMode("level")
                store.saveBatterySaverPercent(20)
            }

            fun openSettingsIfNeeded(design: String) {
                val gear = if (design == "classic") "Быстрые настройки" else "Настройки"
                compose.waitUntil(10_000) {
                    runCatching {
                        compose.onAllNodesWithText("Дизайн").fetchSemanticsNodes().isNotEmpty() ||
                            compose.onAllNodesWithContentDescription(gear).fetchSemanticsNodes().isNotEmpty()
                    }.getOrDefault(false)
                }
                if (compose.onAllNodesWithText("Дизайн").fetchSemanticsNodes().isEmpty()) {
                    compose.onNodeWithContentDescription(gear).performClick()
                }
            }

            val designs = listOf(
                "v2" to "CSQTTR 2.5.3",
                "old" to "Старый",
                "classic" to "CSQTT 2.5.0",
            )
            for (index in designs.indices) {
                val (design, label) = designs[index]
                compose.waitUntil(5000) { runBlocking { store.uiDesign.first() == design } }
                openSettingsIfNeeded(design)
                compose.onNodeWithText("Экстра потоки").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Автопауза Wi-Fi").performScrollTo().assertIsDisplayed()
                val logs = compose.onNodeWithText("Показывать вкладку «Логи»").performScrollTo()
                val before = runBlocking { store.participantShowLogs.first() }
                logs.performClick()
                compose.waitUntil(5000) { runBlocking { store.participantShowLogs.first() != before } }
                compose.onNodeWithText(BatterySaver.TITLE).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("≤ 20 %").assertExists()
                val nextLabel = designs[(index + 1) % designs.size].second
                compose.onNodeWithText(label).performScrollTo().performClick()
                compose.onNodeWithText(nextLabel).performClick()
            }

            compose.waitUntil(5000) { runBlocking { store.uiDesign.first() == "v2" } }
            runBlocking {
                assertEquals(profile, store.activeProfile.first())
                val after = store.readProfileConnectionSettings(profile)
                assertEquals(connection.peer, after.peer)
                assertEquals(connection.serverPeerPort, after.serverPeerPort)
                check(connection.connectionPassword == after.connectionPassword)
                assertEquals(connection.workersPerHash, after.workersPerHash)
            }
        } finally {
            runBlocking {
                store.saveUiDesign(oldDesign)
                store.saveUiOwnerMode(oldOwner)
                store.saveParticipantShowLogs(oldShowLogs)
                store.saveBatterySaverMode(oldMode)
                store.saveBatterySaverPercent(oldPercent)
            }
        }
    }
}
