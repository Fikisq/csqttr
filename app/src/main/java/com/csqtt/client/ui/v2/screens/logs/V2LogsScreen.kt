package com.csqtt.client.ui.v2.screens.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csqtt.client.DeployManager
import com.csqtt.client.LogEntry
import com.csqtt.client.LogLevel
import com.csqtt.client.R
import com.csqtt.client.SettingsStore
import com.csqtt.client.TunnelManager
import com.csqtt.client.showRaisedToast
import com.csqtt.client.ui.v2.components.V2Card
import com.csqtt.client.ui.v2.components.V2TopBar
import com.csqtt.client.ui.v2.components.V2SwitchSetting
import com.csqtt.client.ui.v2.components.V2AdaptiveScreen
import com.csqtt.client.ui.v2.components.LocalV2AdaptiveMetrics
import com.csqtt.client.ui.v2.design.V2TextSizes
import com.csqtt.client.ui.v2.components.V2TransparentIconButton
import com.csqtt.client.ui.v2.design.LocalV2Colors
import com.csqtt.client.ui.v2.design.V2ClassicLightColors
import com.csqtt.client.ui.v2.design.V2MonoFamily
import com.csqtt.client.ui.v2.design.V2Palette
import com.csqtt.client.ui.v2.design.TagColors
import com.csqtt.client.ui.v2.design.V2Shapes
import com.csqtt.client.ui.v2.design.V2Sizes
import com.csqtt.client.ui.v2.design.V2Spacing
import com.csqtt.client.ui.v2.design.V2Theme
import kotlinx.coroutines.launch

@Composable
fun V2LogsScreen(
    settingsStore: SettingsStore,
    version: String,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onVersionClick: (() -> Unit)? = null,
) {
    val colors = V2Theme.colors
    val terminalColors = if (colors.background.luminance() > 0.5f) {
        val surface = if (colors == V2ClassicLightColors) Color(0xFF171B21) else colors.terminal
        colors.copy(
            background = surface,
            surface = surface,
            surfaceElevated = Color(0xFF292D36),
            textPrimary = colors.terminalText,
            textSecondary = Color(0xFFBAC1CE),
        )
    } else colors
    val loggingEnabled by settingsStore.loggingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    V2AdaptiveScreen(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = V2Sizes.ScreenPadding),
    ) {
        V2TopBar(
            version = version,
            title = "Логи",
            onSettings = onSettings,
            onVersionClick = onVersionClick,
        )
        V2Card {
            V2SwitchSetting(stringResource(R.string.logs_enabled), loggingEnabled, { enabled ->
                TunnelManager.isLoggingEnabled = enabled
                scope.launch { settingsStore.saveLoggingEnabled(enabled) }
            })
        }
        Spacer(Modifier.height(LocalV2AdaptiveMetrics.current.gap))
        V2Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            color = terminalColors.surface,
            contentColor = terminalColors.textPrimary,
            shape = RoundedCornerShape(26.dp),
            borderColor = null,
            contentPadding = PaddingValues(0.dp),
            contentModifier = Modifier.fillMaxSize(),
        ) {
            CompositionLocalProvider(LocalV2Colors provides terminalColors) {
                V2LogsTerminal()
            }
        }
    }
}

@Composable
private fun V2LogsTerminal() {
    val context = LocalContext.current
    val currentLogs = TunnelManager.logs.mapNotNull { filterLogEntry(it, "Все уровни", "Все события") }
    val listState = rememberLazyListState()
    val isDeploying by DeployManager.isDeploying.collectAsStateWithLifecycle()

    val latestIndex = if (isDeploying) currentLogs.indexOfLast { it.key.startsWith("deploy_") } else -1
    val latestKey = currentLogs.getOrNull(latestIndex)?.key
    var lastFollowedKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(latestKey) {
        if (latestKey != null && lastFollowedKey != latestKey) {
            listState.scrollToItem(latestIndex)
            lastFollowedKey = latestKey
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentLogs.isEmpty()) Text("Событий пока нет", color = V2Theme.colors.textSecondary, modifier = Modifier.align(Alignment.Center), fontSize = V2TextSizes.Secondary)
        if (currentLogs.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(V2Spacing.Xs),
            ) {
                items(
                    items = currentLogs,
                    key = { it.key },
                    contentType = { "log_line" },
                ) { entry ->
                    V2LogLine(entry)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = TunnelManager::clearLogs,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Очистить логи",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (currentLogs.isEmpty()) {
                                context.showRaisedToast("Логи пусты", android.widget.Toast.LENGTH_SHORT)
                                return@clickable
                            }
                            val text = currentLogs.joinToString("\n") { "${it.message} (x${it.count})" }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("CSQTT Logs", text))
                            context.showRaisedToast("Логи скопированы", android.widget.Toast.LENGTH_SHORT)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Скопировать логи",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun isErrorLog(tag: String, level: LogLevel, isError: Boolean): Boolean {
    return isError || level == LogLevel.ERR ||
        tag.contains("ОШИБКА", ignoreCase = true) ||
        tag.contains("ФАТАЛ", ignoreCase = true) ||
        tag.contains("ERR", ignoreCase = true)
}

private fun isGreenLog(tag: String, level: LogLevel, isError: Boolean): Boolean {
    if (isErrorLog(tag, level, isError)) return false
    return level == LogLevel.OK ||
        level == LogLevel.NET ||
        tag.contains("OK", ignoreCase = true) ||
        tag.contains("NET", ignoreCase = true) ||
        tag.contains("СЕТЬ", ignoreCase = true) ||
        tag.contains("WRAP", ignoreCase = true) ||
        tag.contains("ВОРКЕР", ignoreCase = true) ||
        (tag.contains("ДЕПЛОЙ", ignoreCase = true) && (level == LogLevel.OK || level == LogLevel.NET))
}

private fun resolveTagColors(isGreen: Boolean, isError: Boolean): TagColors {
    if (isError) return V2Palette.TagError
    if (isGreen) return V2Palette.TagSuccess
    return V2Palette.TagInfo
}

private fun parseMessageTag(rawMessage: String, level: LogLevel): Pair<String, String> {
    val trimmed = rawMessage.trim()
    if (trimmed.startsWith("[")) {
        val closeIndex = trimmed.indexOf(']')
        if (closeIndex in 1..24) {
            val tag = trimmed.substring(1, closeIndex).trim()
            val body = trimmed.substring(closeIndex + 1).trim()
            if (tag.isNotEmpty() && body.isNotEmpty()) {
                return tag to body
            }
        }
    }
    return level.name to trimmed
}

private fun parseAnsiLogBody(
    text: String,
    defaultColor: Color,
    accentColor: Color,
    contrastColor: Color,
): AnnotatedString {
    val ansiRegex = Regex("\u001B\\[([0-9;]+)m")
    if (!text.contains("\u001B[")) {
        return AnnotatedString(text)
    }
    val builder = AnnotatedString.Builder()
    var currentIndex = 0
    var currentColor = defaultColor

    for (match in ansiRegex.findAll(text)) {
        if (match.range.first > currentIndex) {
            val segment = text.substring(currentIndex, match.range.first)
            builder.pushStyle(SpanStyle(color = currentColor))
            builder.append(segment)
            builder.pop()
        }
        val code = match.groupValues[1]
        currentColor = when (code) {
            "32", "92" -> accentColor
            "31", "91" -> contrastColor
            "0" -> defaultColor
            else -> currentColor
        }
        currentIndex = match.range.last + 1
    }
    if (currentIndex < text.length) {
        val segment = text.substring(currentIndex)
        builder.pushStyle(SpanStyle(color = currentColor))
        builder.append(segment)
        builder.pop()
    }
    return builder.toAnnotatedString()
}

@Composable
private fun V2LogLine(
    entry: LogEntry,
    modifier: Modifier = Modifier,
) {
    val colors = V2Theme.colors
    val level = effectiveLogLevel(entry)

    val (rawTag, body) = parseMessageTag(entry.message, level)
    val isError = isErrorLog(rawTag, level, entry.isError)
    val light = colors.background.luminance() > 0.5f
    val errorColor = if(light) Color(0xFFB32626) else Color(0xFFFF897D)
    val successColor = if(light) Color(0xFF186C3C) else Color(0xFF4ADE80)

    // Harmonious palette-based tag colors (no harsh bright red / green)
    val green = isGreenLog(rawTag, level, entry.isError)
    val tagBg = if(light) when { isError -> Color(0xFFFBE2E2); green -> Color(0xFFE1F3E7); else -> Color(0xFFE4EEFC) }
        else when { isError -> Color(0xFF3E1218); green -> Color(0xFF052E16); else -> Color(0xFF0A223D) }
    val tagText = when { isError -> errorColor; green -> successColor; light -> colors.accent; else -> Color(0xFF3DBFFD) }

    // Clean legible text from the theme palette
    val bodyColor = when { isError -> errorColor; green -> successColor; level == LogLevel.DBG -> colors.textSecondary; else -> colors.textPrimary }

    val annotatedBody = remember(body, bodyColor, colors.accent, colors.contrast) {
        parseAnsiLogBody(
            text = body,
            defaultColor = bodyColor,
            accentColor = successColor,
            contrastColor = errorColor,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(V2Spacing.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = V2Shapes.Pill,
            color = tagBg,
            contentColor = tagText,
            border = null,
            modifier = Modifier.defaultMinSize(minHeight = 22.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = rawTag.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    ),
                    fontFamily = V2MonoFamily,
                    maxLines = 1,
                )
            }
        }

        Text(
            text = annotatedBody,
            color = bodyColor,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
            fontFamily = V2MonoFamily,
            fontWeight = if (isError) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        if (entry.count > 1) {
            Surface(
                shape = V2Shapes.Pill,
                color = colors.surfaceElevated,
                contentColor = colors.textSecondary,
                border = null,
                modifier = Modifier.defaultMinSize(minHeight = 18.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "x${entry.count}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        fontFamily = V2MonoFamily,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
