package com.csqtt.client.ui.legacy

import com.csqtt.client.GoobyMode
import com.csqtt.client.CsqttConstants

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.csqtt.client.ui.legacy.design.CsqttShapes

data class ModeIndicator(
    val progress: Float,
    val color: Color,
)

internal const val GOOBY_HELP =
    "Gooby меняет отправку первых данных при подключении по TCP/TLS. После установления соединения трафик передаётся без этих задержек. При UDP выбранный режим сохраняется, а Gooby отключён."

internal fun goobyModeHelp(mode: GoobyMode): String = when (mode) {
    GoobyMode.OFF -> "Первые данные TCP/TLS отправляются без обработки Gooby."
    GoobyMode.GENTLE -> "Заголовок TURN Allocate делится на 2 части с паузой 1 мс. Имя сервера в TLS ClientHello разбивается на фрагменты до 8 байт с паузой 1 мс. TTL не уменьшается."
    GoobyMode.CURRENT -> "Заголовок TURN Allocate делится на 4 части с паузами 5 мс. Имя сервера в TLS ClientHello разбивается на фрагменты до 2 байт с паузами 10 мс. При первой отправке TLS используется пониженный TTL."
    GoobyMode.EXTENDED -> "Заголовок TURN Allocate делится на 6 частей с паузами 10 мс. Имя сервера в TLS ClientHello разбивается по одному байту с паузами 15 мс. При первой отправке TLS используется пониженный TTL."
}

internal const val SRTP_HELP = "SRTP определяет, как пакеты туннеля выглядят для сети."

internal fun srtpModeHelp(mode: String): String = when (mode) {
    CsqttConstants.SrtpMode.DTLS -> "Полное DTLS-рукопожатие и защищённый SRTP."
    CsqttConstants.SrtpMode.WRAP -> "SRTP-обёртка без DTLS-рукопожатия."
    else -> "Сначала DTLS-SRTP. При неудаче — WRAP-SRTP."
}

internal fun goobyIndicator(mode: GoobyMode, color: Color): ModeIndicator? =
    when (mode) {
        GoobyMode.OFF -> null
        GoobyMode.GENTLE -> ModeIndicator(1f / 3f, color)
        GoobyMode.CURRENT -> ModeIndicator(2f / 3f, color)
        GoobyMode.EXTENDED -> ModeIndicator(1f, color)
    }

@Composable
internal fun ModeProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0.05f, 1f),
        label = "mode_progress",
    )
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
internal fun CompactDropdownSetting(
    title: String,
    selectedKey: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTitleInfo: (() -> Unit)? = null,
    titleInfoIcon: ImageVector = Icons.AutoMirrored.Filled.HelpOutline,
    onInfo: ((String) -> Unit)? = null,
    onItemEdit: ((String) -> Unit)? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    indicatorProvider: ((String) -> ModeIndicator?)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second.orEmpty()
    val currentIndicator = indicatorProvider?.invoke(selectedKey)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (onTitleInfo != null) {
                IconButton(
                    onClick = onTitleInfo,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = titleInfoIcon,
                        contentDescription = "Информация: $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        leadingContent?.invoke(this)
        Box(modifier = Modifier.widthIn(min = 90.dp, max = 135.dp)) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = CsqttShapes.Pill,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 5.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedLabel,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    if (currentIndicator != null) {
                        Spacer(Modifier.height(2.dp))
                        ModeProgressBar(
                            progress = currentIndicator.progress,
                            color = currentIndicator.color,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 160.dp, max = 220.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                options.forEach { (key, label) ->
                    val itemIndicator = indicatorProvider?.invoke(key)
                    DropdownMenuItem(
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(start = 5.dp, top = 2.dp, bottom = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (itemIndicator != null) {
                                    ModeProgressBar(
                                        progress = itemIndicator.progress,
                                        color = itemIndicator.color,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        },
                        trailingIcon = if (onItemEdit != null || onInfo != null) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (onItemEdit != null) {
                                        IconButton(
                                            onClick = {
                                                expanded = false
                                                onItemEdit(key)
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Редактировать: $label",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    if (onInfo != null) {
                                        IconButton(
                                            onClick = {
                                                expanded = false
                                                onInfo(key)
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                                contentDescription = "Информация: $label",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            onSelected(key)
                        },
                    )
                }
            }
        }
    }
}
