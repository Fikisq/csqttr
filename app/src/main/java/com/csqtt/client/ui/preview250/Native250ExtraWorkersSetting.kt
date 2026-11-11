package com.csqtt.client.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.csqtt.client.CsqttConstants
import com.csqtt.client.ui.components.CsqttSettingRow

@Composable
internal fun Native250ExtraWorkersSetting(profile: Int, authMode: String) {
    val selected = rememberNative250Preference("extraWorkers.$profile", "false")
    val allowed = authMode != CsqttConstants.VkAuth.MODE_AUTO_JS
    var showHelp by rememberSaveable { mutableStateOf(false) }
    CsqttSettingRow(
        title = "Экстра потоки",
        checked = allowed && selected.value.toBoolean(),
        enabled = allowed,
        onCheckedChange = { if (allowed) selected.set(it.toString()) },
        onInfoClick = { showHelp = true },
        infoContentDescription = "Как работают экстра потоки",
    )
    if (showHelp) Native250PreviewDialog("Экстра потоки", { showHelp = false }, showClose = false, confirmation = "Понятно") {
        Native250HelpText("• Функция повышает максимальное количество потоков с 70 до 100.\n\n• Во время подключения выбор сохраняется для следующего запуска туннеля.\n\n• Значения выбираются кратно 10 потокам.\n\n• В режиме «Авто ВК» функция недоступна.")
    }
}
