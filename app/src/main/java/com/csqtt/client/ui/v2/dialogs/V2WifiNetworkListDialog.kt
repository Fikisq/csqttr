package com.csqtt.client.ui.v2.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import com.csqtt.client.SettingsStore
import com.csqtt.client.ui.legacy.WifiAutoPauseContent
import com.csqtt.client.ui.v2.components.V2DialogContainer

@Composable
fun V2WifiNetworkListDialog(settingsStore: SettingsStore, onDismiss: () -> Unit) {
    V2DialogContainer(title = "Автопауза Wi-Fi", icon = Icons.Default.Wifi, onDismiss = onDismiss) {
        WifiAutoPauseContent(settingsStore)
    }
}
