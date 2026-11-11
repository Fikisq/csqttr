package com.csqtt.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.csqtt.client.CsqttConstants

@Composable
internal fun Native250GoobySetting(profile: Int, transport: String) {
    val selected = rememberNative250Preference("gooby.$profile", "off")
    val choices = listOf("off" to "Выкл", "gentle" to "Легкая", "current" to "Средняя", "extended" to "Тяжелая")
    val tcp = transport == CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
    var help by rememberSaveable { mutableStateOf<String?>(null) }
    // Reset an open menu on profile/transport changes, but retain the saved strategy.
    key(profile, transport) {
        CompactDropdownSetting(
            title = "Gooby",
            selectedKey = selected.value.takeIf { value -> choices.any { it.first == value } } ?: "off",
            options = choices,
            enabled = tcp,
            onSelected = { if (tcp) selected.set(it) },
            onTitleInfo = { help = "all" },
            onInfo = { help = it },
            indicatorProvider = { strategy ->
                when (strategy) {
                    "gentle" -> ModeIndicator(1f / 3f, Color(0xFF43A047))
                    "current" -> ModeIndicator(2f / 3f, Color(0xFF43A047))
                    "extended" -> ModeIndicator(1f, Color(0xFF43A047))
                    else -> null
                }
            },
        )
    }
    help?.let { mode ->
        Native250PreviewDialog(
            title = if (mode == "all") "Gooby" else "Gooby · ${choices.first { it.first == mode }.second}",
            onDismiss = { help = null }, showClose = false, confirmation = "Понятно",
        ) {
            Text("Для чего нужен Gooby", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Native250HelpText("Gooby применяется только при транспорте TCP. При UDP он не запускается.")
            Text("Выбор режима", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Native250HelpText("Выкл — обычное подключение без Gooby.\n\nЛегкая, Средняя и Тяжелая — три доступные стратегии. Названия не гарантируют прирост скорости или успешное подключение: результат зависит от сети.")
            Text("При смене транспорта", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Native250HelpText("Выбранная стратегия сохраняется. При UDP список неактивен; после возврата к TCP выбранный режим снова доступен.")
        }
    }
}
