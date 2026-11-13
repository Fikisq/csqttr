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

    @Test fun deployProgressIsVisibleAfterReturningToTheTabInAllDesigns() {
        val store = SettingsStore(compose.activity)
        val oldDesign = runBlocking { store.uiDesign.first() }
        val oldOwner = runBlocking { store.uiOwnerMode.first() }
        try {
            runBlocking { store.saveUiOwnerMode(true) }
            DeployManager.startDeploy()
            DeployManager.updateProgress(0.43f, "Бинарник...")
            for (design in listOf("v2", "old", "classic")) {
                runBlocking { store.saveUiDesign(design) }
                val deploy = compose.onAllNodesWithText("Деплой")
                compose.waitUntil(10_000) { deploy.fetchSemanticsNodes().isNotEmpty() }
                deploy.onLast().performClick()
                compose.onNodeWithTag("deploy-progress").assertIsDisplayed()
                compose.onNodeWithText("Бинарник...").assertIsDisplayed()
                compose.onNodeWithText("43%").assertIsDisplayed()
                compose.onNodeWithText("Подкл.").performClick()
                deploy.onLast().performClick()
                compose.onNodeWithTag("deploy-progress").assertIsDisplayed()
                compose.onNodeWithText("43%").assertIsDisplayed()
            }
        } finally {
            DeployManager.stopDeploy()
            runBlocking {
                store.saveUiDesign(oldDesign)
                store.saveUiOwnerMode(oldOwner)
            }
        }
    }

    @Test fun tunnelProfilePickerUpdatesTheSelectionOnConnection() {
        val store = SettingsStore(compose.activity)
        val previousDesign = runBlocking { store.uiDesign.first() }
        val previousProfile = runBlocking { store.activeProfile.first() }
        val previousOwner = runBlocking { store.uiOwnerMode.first() }
        val first = runBlocking { checkNotNull(store.findFirstFreeProfile()) }
        var second: Int? = null
        try {
            runBlocking {
                store.saveUiDesign("v2")
                store.saveUiOwnerMode(false)
                store.saveProfileConnectionSettings(first, "203.0.113.216", 46000, "FixturePasswordA", 20)
                store.saveProfileName(first, "Picker fixture A")
                second = checkNotNull(store.findFirstFreeProfile())
                val target = checkNotNull(second)
                store.saveProfileConnectionSettings(target, "203.0.113.217", 46000, "FixturePasswordB", 20)
                store.saveProfileName(target, "Picker fixture B")
                store.saveActiveProfile(first)
            }
            val target = checkNotNull(second)
            compose.onNodeWithText("Туннель").performClick()
            compose.onNodeWithText("Picker fixture A").performClick()
            compose.onNodeWithText("Picker fixture B").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { store.activeProfile.first() == target } }
            compose.onNodeWithText("203.0.113.217").assertIsDisplayed()
            compose.onNodeWithText("Подкл.").performClick()
            compose.waitForIdle()
            assertEquals(target, runBlocking { store.activeProfile.first() })
        } finally {
            runBlocking {
                store.clearProfile(first)
                second?.let { store.clearProfile(it) }
                store.saveActiveProfile(previousProfile)
                store.saveUiDesign(previousDesign)
                store.saveUiOwnerMode(previousOwner)
            }
        }
    }

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
