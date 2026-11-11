package com.csqtt.client.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
internal fun Native250BatterySetting() {
    val mode = rememberNative250Preference("battery.mode", "auto")
    val level = rememberNative250Preference("battery.percent", "30")
    val modes = listOf("auto" to "Авто", "level" to "Свой уровень", "off" to "Выкл")
    val selectedMode = mode.value.takeIf { value -> modes.any { it.first == value } } ?: "auto"
    val percent = level.value.toIntOrNull()?.takeIf { it in 1..100 } ?: 30
    var showHelp by rememberSaveable { mutableStateOf(false) }
    CompactDropdownSetting(
        title = "Экономия батареи", selectedKey = selectedMode, options = modes, enabled = true,
        onSelected = mode.set, onTitleInfo = { showHelp = true },
    )
    // Match our settings: the threshold is visible but inactive in Auto, and hidden in Off.
    if (selectedMode != "off") CompactDropdownSetting(
        title = "Включать при заряде", selectedKey = percent.toString(),
        options = (listOf(10, 15, 20, 25, 30, 40, 50) + percent).distinct().sorted().map { it.toString() to "≤ $it %" },
        enabled = selectedMode == "level", onSelected = { if (selectedMode == "level") level.set(it) },
    )
    if (showHelp) Native250PreviewDialog("Экономия батареи", { showHelp = false }, showClose = false, confirmation = "Понятно") {
        Native250HelpText("Когда экономия включена, CSQTT перестаёт держать процессор телефона включённым ради туннеля.\n\n" +
            "С выключенным экраном телефон расходует заметно меньше заряда, но туннель засыпает вместе с ним: " +
            "сообщения, звонки и уведомления, которые идут через VPN, могут приходить с большой задержкой, а загрузки " +
            "останавливаются. Когда экран включается, туннель продолжает работу.\n\n" +
            "Авто — экономия работает, пока включён режим энергосбережения телефона. Свой уровень — когда заряд " +
            "опустится до выбранного значения, а телефон не заряжается. Выкл — туннель никогда не засыпает.")
    }
}
