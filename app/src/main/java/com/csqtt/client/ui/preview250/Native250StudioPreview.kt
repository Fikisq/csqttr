package com.csqtt.client.ui

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.csqtt.client.CSQTTTheme
import com.csqtt.client.CsqttConstants
import com.csqtt.client.MainScreen
import com.csqtt.client.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The real 2.5.0 screen and dialogs; only their data source is replaced for Layoutlib. */
@Preview(name = "01 Туннель + Gooby", group = "CSQTT 2.5.0", widthDp = 412, heightDp = 732,
    locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun Native250TunnelPreview() = Native250StudioScreen(settings = false)

@Preview(name = "02 Наши настройки", group = "CSQTT 2.5.0", widthDp = 412, heightDp = 732,
    locale = "ru", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun Native250SettingsPreview() = Native250StudioScreen(settings = true)

@Composable
private fun Native250StudioScreen(settings: Boolean) {
    val context = LocalContext.current
    val store = remember(context) {
        // Supply the same initial values used by the original screen's flow collectors.
        SettingsStore.cachedVkHashMode = CsqttConstants.VkAutoHash.MODE_MANUAL
        SettingsStore.cachedVkAccessToken = ""
        SettingsStore.cachedTurnTransport = CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
        SettingsStore.cachedWorkersPerHash = 1
        SettingsStore(context, Native250MemoryDataStore())
    }
    val values = remember { mutableStateMapOf<String, String>() }
    var theme by remember { mutableStateOf(CsqttConstants.Theme.MODE_DARK) }
    CompositionLocalProvider(LocalNative250StudioValues provides values) {
        CSQTTTheme(themeMode = theme) {
            MainScreen(settingsStore = store, themeMode = theme, onThemeChange = { theme = it },
                initialTab = 1, initiallyShowSettings = settings)
        }
    }
}

/** Layoutlib has no disk-backed DataStore; preview edits live only until refresh. */
private class Native250MemoryDataStore : DataStore<Preferences> {
    override val data = MutableStateFlow(preferencesOf(
        stringPreferencesKey("turn_transport") to CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
        stringPreferencesKey("vk_hash_mode") to CsqttConstants.VkAutoHash.MODE_MANUAL,
        booleanPreferencesKey("tcp_transport_risk_acknowledged") to true,
    ))
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(data.value).toPreferences().also { data.value = it } }
}
