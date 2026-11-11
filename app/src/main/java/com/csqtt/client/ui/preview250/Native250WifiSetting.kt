package com.csqtt.client.ui

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.csqtt.client.currentWifiSsid
import com.csqtt.client.hasWifiSsidPermission
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.csqtt.client.ui.components.CsqttSettingRow
import com.csqtt.client.ui.design.CsqttShapes
import com.csqtt.client.ui.design.CsqttSizes

private val wifiRules = listOf("all" to "На любом Wi-Fi", "blacklist" to "Только в выбранных сетях", "whitelist" to "Во всех, кроме выбранных")

@Composable
internal fun Native250WifiSetting() {
    val enabled = rememberNative250Preference("wifi.enabled", "false")
    var showNetworks by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    CsqttSettingRow(
        title = "Автопауза при Wi-Fi", checked = enabled.value.toBoolean(),
        onCheckedChange = { enabled.set(it.toString()) },
        onInfoClick = { showHelp = true }, infoContentDescription = "Как работает автопауза при Wi-Fi",
    )
    Native250OutlineButton("Настройки автопаузы", onClick = { showNetworks = true })
    if (showNetworks) Native250WifiDialog(onDismiss = { showNetworks = false })
    if (showHelp) Native250PreviewDialog("Автопауза при Wi-Fi", { showHelp = false }, showClose = false, confirmation = "Понятно") {
        Native250HelpText("Когда функция включена и устройство подключается к Wi-Fi, CSQTT переходит в ожидание и отключает свой VPN-сервис. После отключения Wi-Fi CSQTT автоматически возвращается к обычному подключению, если до паузы он был включён.\n\nПо умолчанию функция выключена.")
        Native250HelpText("На любом Wi-Fi — ставить VPN на паузу при подключении к любой Wi-Fi-сети.\n\nТолько в выбранных сетях — ставить на паузу в отмеченных сетях.\n\nВо всех, кроме выбранных — продолжать работу VPN в отмеченных сетях.\n\nСписок сетей находится в «Настройках автопаузы».")
    }
}

@Composable
private fun Native250WifiDialog(onDismiss: () -> Unit) {
    val enabled = rememberNative250Preference("wifi.enabled", "false")
    val rule = rememberNative250Preference("wifi.rule", "all")
    val storedNetworks = rememberNative250Preference("wifi.networks", "{}")
    val networks = remember(storedNetworks.value) { Native250WifiNetworks.decode(storedNetworks.value) }
    val activeRule = rule.value.takeIf { candidate -> wifiRules.any { it.first == candidate } } ?: "all"
    val canSelect = enabled.value.toBoolean() && activeRule != "all"
    val context = LocalContext.current.applicationContext
    var current by remember { mutableStateOf(readNative250WifiName(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        current = readNative250WifiName(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { current = readNative250WifiName(context) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Native250PreviewDialog("Автопауза при Wi-Fi", onDismiss) {
        CsqttSettingRow("Автопауза при Wi-Fi", enabled.value.toBoolean(), { enabled.set(it.toString()) })
        Text("Когда ставить VPN на паузу", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            wifiRules.forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().selectable(
                    selected = activeRule == id, enabled = enabled.value.toBoolean(), role = Role.RadioButton,
                    onClick = { rule.set(id) },
                ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(activeRule == id, onClick = null, enabled = enabled.value.toBoolean())
                    Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Текущая сеть", style = MaterialTheme.typography.titleSmall)
        val currentName = current
        if (currentName == null) {
            Text("Имя сети недоступно", style = MaterialTheme.typography.bodyMedium)
            Native250OutlineButton("Разрешить доступ к имени сети", onClick = {
                if (!hasWifiSsidPermission(context)) {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        } else {
            Native250WifiRow(currentName, currentName in networks.selected, canSelect && currentName in networks.saved,
                onSelect = { storedNetworks.set(networks.select(currentName, it).encode()) })
            if (currentName !in networks.saved) Native250OutlineButton(
                if (activeRule == "whitelist") "Запомнить текущую сеть и исключить" else "Запомнить текущую сеть",
                onClick = { storedNetworks.set(networks.remember(currentName, activeRule).encode()) },
            )
        }
        Text("Сохранённые сети", style = MaterialTheme.typography.titleSmall)
        if (activeRule != "all" && Build.VERSION.SDK_INT >= 29 && hasWifiSsidPermission(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Text("Для распознавания сети в фоне разрешите геолокацию «Всегда» в настройках приложения. Координаты не запрашиваются.", style = MaterialTheme.typography.bodySmall)
            Native250OutlineButton("Доступ в фоне", onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        if (networks.saved.isEmpty()) Text("Нет сохранённых сетей", style = MaterialTheme.typography.bodySmall)
        networks.saved.sorted().forEach { name ->
            key(name) {
                Native250WifiRow(name, name in networks.selected, canSelect,
                    onSelect = { storedNetworks.set(networks.select(name, it).encode()) }, onDelete = { deleting = name })
            }
        }
        Native250OutlineButton("Добавить вручную", onClick = { input = ""; error = null; showAdd = true })
    }
    if (showAdd) Native250PreviewDialog("Добавить сеть", { showAdd = false }) {
        OutlinedTextField(input, { input = it; error = null }, Modifier.fillMaxWidth(),
            label = { Text("Имя сети (SSID)") }, singleLine = true, shape = CsqttShapes.Control,
            isError = error != null, supportingText = { error?.let { Text(it) } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showAdd = false }) { Text("Отмена") }
            TextButton(onClick = {
                val name = native250Ssid(input)
                when {
                    name == null -> error = "Введите имя Wi-Fi длиной до 32 байт"
                    name in networks.saved -> error = "Эта сеть уже сохранена"
                    else -> { storedNetworks.set(networks.remember(name, activeRule).encode()); showAdd = false }
                }
            }) { Text(if (activeRule == "whitelist") "Запомнить и исключить" else "Запомнить") }
        }
    }
    deleting?.let { name ->
        Native250PreviewDialog("Удалить сеть?", { deleting = null }) {
            Native250HelpText("$name будет удалена из раздела «Сохранённые сети» и из выбранного списка автопаузы.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { deleting = null }) { Text("Отмена") }
                TextButton(onClick = { storedNetworks.set(networks.forget(name).encode()); deleting = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun Native250WifiRow(name: String, checked: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit, onDelete: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onSelect)
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, onCheckedChange = null, enabled = enabled)
            Text(name, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        if (onDelete != null) IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Удалить $name", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun readNative250WifiName(context: Context): String? = currentWifiSsid(context)?.let(::native250Ssid)

@Composable
private fun Native250OutlineButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = CsqttSizes.ControlHeight), shape = CsqttShapes.Pill,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
