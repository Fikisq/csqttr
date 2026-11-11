package com.csqtt.client.ui.legacy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csqtt.client.SavedWifiNetworks
import com.csqtt.client.SettingsStore
import com.csqtt.client.WifiNetworkListMode
import com.csqtt.client.currentWifiSsid
import com.csqtt.client.hasWifiSsidPermission
import com.csqtt.client.validWifiSsid
import com.csqtt.client.ui.legacy.components.CsqttSettingRow
import kotlinx.coroutines.launch

@Composable
internal fun WifiNetworkListDialog(settingsStore: SettingsStore, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопауза при Wi-Fi", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                WifiAutoPauseContent(settingsStore)
            }
        }
    }
}

@Composable
internal fun WifiAutoPauseContent(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by settingsStore.autoPauseOnWifi.collectAsStateWithLifecycle(false)
    val modeValue by settingsStore.wifiNetworkListMode.collectAsStateWithLifecycle(WifiNetworkListMode.ALL.storedValue)
    val networks by settingsStore.savedWifiNetworks.collectAsStateWithLifecycle(SavedWifiNetworks())
    val mode = WifiNetworkListMode.fromStored(modeValue)
    val canSelect = enabled && mode != WifiNetworkListMode.ALL
    var currentSsid by remember { mutableStateOf(currentWifiSsid(context)) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentSsid = currentWifiSsid(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { currentSsid = currentWifiSsid(context) }

    CsqttSettingRow("Автопауза при Wi-Fi", enabled, { value ->
        scope.launch { settingsStore.saveAutoPauseOnWifi(value) }
    })
    Text("Когда ставить VPN на паузу", style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        WifiNetworkListMode.entries.forEach { rule ->
            Row(
                Modifier.fillMaxWidth().selectable(mode == rule, enabled, Role.RadioButton) {
                    scope.launch { settingsStore.saveWifiNetworkListMode(rule.storedValue) }
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == rule, onClick = null, enabled = enabled)
                Text(rule.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Text("Текущая сеть", style = MaterialTheme.typography.titleSmall)
    val current = currentSsid
    if (current != null) {
        WifiSelectionRow(current, current in networks.selected, canSelect && current in networks.saved, {
            scope.launch { settingsStore.setWifiNetworkSelected(current, it) }
        })
        if (current !in networks.saved) {
            TextButton(onClick = { scope.launch { settingsStore.rememberWifiNetwork(current) } }) {
                Text(if (mode == WifiNetworkListMode.WHITELIST) "Запомнить текущую сеть и исключить" else "Запомнить текущую сеть")
            }
        }
    } else {
        Text("Имя сети недоступно", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = {
            if (hasWifiSsidPermission(context)) {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } else {
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }) { Text("Разрешить доступ к имени сети") }
    }

    Text("Сохранённые сети", style = MaterialTheme.typography.titleSmall)
    if (mode != WifiNetworkListMode.ALL && Build.VERSION.SDK_INT >= 29 && hasWifiSsidPermission(context) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Text("Для распознавания Wi-Fi в фоне разрешите геолокацию «Всегда».", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Доступ в фоне") }
    }
    if (networks.saved.isEmpty()) Text("Нет сохранённых сетей", style = MaterialTheme.typography.bodySmall)
    networks.saved.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { name ->
        key(name) {
            WifiSelectionRow(name, name in networks.selected, canSelect, {
                scope.launch { settingsStore.setWifiNetworkSelected(name, it) }
            }, onDelete = { deleting = name })
        }
    }
    TextButton(onClick = { input = ""; showAdd = true }) { Text("Добавить вручную") }

    if (showAdd) {
        val name = validWifiSsid(input)
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Добавить сеть") },
            text = {
                OutlinedTextField(input, { input = it }, singleLine = true, label = { Text("Имя сети (SSID)") },
                    supportingText = {
                        if (name != null && name in networks.saved) Text("Эта сеть уже сохранена")
                        else Text("До 32 байт")
                    })
            },
            confirmButton = {
                TextButton(enabled = name != null && name !in networks.saved, onClick = {
                    if (name != null) scope.launch { settingsStore.rememberWifiNetwork(name) }
                    showAdd = false
                }) { Text(if (mode == WifiNetworkListMode.WHITELIST) "Запомнить и исключить" else "Запомнить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } },
        )
    }
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить сеть?") },
            text = { Text("«$name» будет удалена из сохранённых сетей и из правила автопаузы.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { settingsStore.forgetWifiNetwork(name) }; deleting = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun WifiSelectionRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = onSelect)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(selected, onCheckedChange = null, enabled = enabled)
            Text(name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Удалить $name", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
