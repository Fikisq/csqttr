package com.csqtt.client

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
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

    @Test fun settingsClipboardRoundTripRestoresTheActiveProfileInEveryDesign() {
        val store = SettingsStore(compose.activity)
        val previous = runBlocking { store.activeProfile.first() }
        val previousDesign = runBlocking { store.uiDesign.first() }
        val untouched = runBlocking { store.readProfileConnectionSettings(previous) }
        val slot = runBlocking { checkNotNull(store.findFirstFreeProfile()) }
        val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
        try {
            runBlocking {
                store.saveProfileConnectionSettings(slot, "203.0.113.61", 46123, "TransferFixture61", 40)
                store.saveProfileName(slot, "Clipboard transfer fixture")
                store.saveActiveProfile(slot)
                store.saveAppRoutingLists("com.example.before", "", false)
            }
            for (design in listOf("v2", "old", "classic")) {
                runBlocking { store.saveUiDesign(design) }
                val settings = if (design == "classic") "Быстрые настройки" else "Настройки"
                val settingsButton = compose.onAllNodesWithContentDescription(settings)
                compose.waitUntil(10_000) { settingsButton.fetchSemanticsNodes().isNotEmpty() }
                settingsButton.onFirst().performClick()
                compose.onNodeWithText("Импорт / экспорт").performScrollTo().performClick()
                compose.runOnIdle { clipboard.clearPrimaryClip() }
                compose.onNodeWithText("Копировать в буфер").performScrollTo().performClick()
                compose.waitUntil(5000) {
                    compose.runOnIdle {
                        clipboard.primaryClip?.getItemAt(0)?.text?.contains("203.0.113.61") == true
                    }
                }
                runBlocking {
                    store.saveProfileConnectionSettings(slot, "203.0.113.62", 46124, "TransferFixture62", 20)
                    store.saveAppRoutingLists("com.example.after", "", false)
                }
                compose.onNodeWithText("Импорт").performClick()
                compose.onNodeWithText("Из буфера").performScrollTo().performClick()
                compose.waitUntil(5000) {
                    runBlocking { store.readProfileConnectionSettings(slot).peer == "203.0.113.61" }
                }
                runBlocking {
                    val restored = store.readProfileConnectionSettings(slot)
                    assertEquals(46123, restored.serverPeerPort)
                    check(restored.connectionPassword == "TransferFixture61")
                    assertEquals(40, restored.workersPerHash)
                    assertEquals("com.example.before", store.excludedApps.first())
                    assertEquals(slot, store.activeProfile.first())
                    check(untouched == store.readProfileConnectionSettings(previous))
                }
                val close = compose.onAllNodesWithContentDescription("Закрыть")
                compose.waitUntil(5000) { close.fetchSemanticsNodes().isNotEmpty() }
                close.onFirst().performClick()
            }
        } finally {
            compose.runOnIdle { clipboard.clearPrimaryClip() }
            runBlocking {
                store.saveActiveProfile(previous)
                store.clearProfile(slot)
                store.saveUiDesign(previousDesign)
            }
        }
    }

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

    @Test fun classicSettingsCanDeleteOnlyTheChosenProfile() {
        val store = SettingsStore(compose.activity)
        val previous = runBlocking { store.activeProfile.first() }
        val previousDesign = runBlocking { store.uiDesign.first() }
        val other = runBlocking { store.readProfileConnectionSettings(previous) }
        val slot = runBlocking { checkNotNull(store.findFirstFreeProfile()) }
        val name = "Classic delete fixture"
        try {
            runBlocking {
                store.saveProfileConnectionSettings(slot, "203.0.113.44", 46000, "DeleteFixture44", 20)
                store.saveProfileName(slot, name)
                store.saveActiveProfile(slot)
                store.saveUiDesign("classic")
            }
            val gear = compose.onAllNodesWithContentDescription("Быстрые настройки")
            compose.waitUntil(10_000) { gear.fetchSemanticsNodes().isNotEmpty() }
            gear.onFirst().performClick()
            compose.onNodeWithText(name).performClick()
            compose.onNodeWithContentDescription("Редактировать: $name").performScrollTo().performClick()
            compose.onNodeWithText("Удалить профиль").performClick()
            compose.onNodeWithText("Отмена").performClick()
            check(slot in runBlocking { store.occupiedProfiles.first() })
            compose.onNodeWithText("Удалить профиль").performClick()
            compose.onNodeWithText("Удалить").performClick()
            compose.waitUntil(5_000) { slot !in runBlocking { store.occupiedProfiles.first() } }
            check(slot != runBlocking { store.activeProfile.first() } || slot == previous)
            check(other == runBlocking { store.readProfileConnectionSettings(previous) })
        } finally {
            runBlocking {
                store.saveActiveProfile(previous)
                store.clearProfile(slot)
                store.saveUiDesign(previousDesign)
            }
        }
    }

    @Test fun oldDesignUsesPaleBlueForTheConnectedButtonInBothThemes() {
        val store = SettingsStore(compose.activity)
        val oldDesign = runBlocking { store.uiDesign.first() }
        val oldTheme = runBlocking { store.themeMode.first() }
        val oldRunning = TunnelManager.running.value
        try {
            runBlocking { store.saveUiDesign("old") }
            compose.onAllNodesWithText("Подкл.").onFirst().performClick()
            compose.runOnIdle { TunnelManager.running.value = true }
            for (theme in listOf("light", "dark")) {
                runBlocking { store.saveThemeMode(theme) }
                val pixels = compose.onNodeWithContentDescription("Отключить", useUnmergedTree = true)
                    .captureToImage().toPixelMap()
                val paleBlue = 0xFFD2E8FF.toInt()
                check((0 until pixels.height).any { y ->
                    (0 until pixels.width).any { x -> pixels[x, y].toArgb() == paleBlue }
                }) { "Connected button is missing the pale blue icon in theme $theme" }
            }
        } finally {
            compose.runOnIdle { TunnelManager.running.value = oldRunning }
            runBlocking {
                store.saveThemeMode(oldTheme)
                store.saveUiDesign(oldDesign)
            }
        }
    }

    @Test fun goobyHelpExplainsEachPresetInAllThreeDesigns() {
        val store = SettingsStore(compose.activity)
        val oldDesign = runBlocking { store.uiDesign.first() }
        val profile = runBlocking { store.activeProfile.first() }
        val oldTransport = runBlocking { store.turnTransport.first() }
        val oldPreset = runBlocking { store.goobyMode.first() }
        val descriptions = mapOf(
            "Выкл" to "без обработки Gooby",
            "Лёгкая" to "делится на 2 части",
            "Средняя" to "делится на 4 части",
            "Тяжёлая" to "делится на 6 частей",
        )
        try {
            runBlocking {
                store.saveTurnTransport(CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS)
                store.saveGoobyMode(GoobyMode.GENTLE, profile)
            }
            for (design in listOf("v2", "old", "classic")) {
                runBlocking { store.saveUiDesign(design) }
                val tunnel = compose.onAllNodesWithText("Туннель")
                compose.waitUntil(10_000) { tunnel.fetchSemanticsNodes().isNotEmpty() }
                tunnel.onFirst().performClick()
                val selectedPreset = compose.onNodeWithText("Лёгкая")
                for ((label, description) in descriptions) {
                    selectedPreset.performScrollTo().performClick()
                    compose.onNodeWithContentDescription("Информация: $label").performClick()
                    compose.onNodeWithText(description, substring = true).assertIsDisplayed()
                    compose.onNodeWithText("Понятно").performClick()
                }
                assertEquals(GoobyMode.GENTLE, runBlocking { store.goobyMode.first() })
            }
        } finally {
            runBlocking {
                store.saveTurnTransport(oldTransport)
                store.saveGoobyMode(oldPreset, profile)
                store.saveUiDesign(oldDesign)
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
