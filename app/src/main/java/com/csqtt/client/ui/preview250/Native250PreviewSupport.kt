package com.csqtt.client.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.csqtt.client.ui.design.CsqttShapes
import com.csqtt.client.ui.design.CsqttSizes

internal data class Native250PreviewValue(val value: String, val set: (String) -> Unit)

/** Preview state shared by nested dialogs, without disk or Android preferences. */
internal val LocalNative250StudioValues = staticCompositionLocalOf<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>?> { null }

/** UI-only choices: never write SettingsStore or control the original tunnel. */
@Composable
internal fun rememberNative250Preference(key: String, default: String): Native250PreviewValue {
    val studioValues = LocalNative250StudioValues.current
    if (studioValues != null) {
        return Native250PreviewValue(studioValues[key] ?: default) { studioValues[key] = it }
    }
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences("native250_ui_preview", Context.MODE_PRIVATE)
    }
    var value by remember(preferences, key) {
        mutableStateOf(preferences.getString(key, default) ?: default)
    }
    DisposableEffect(preferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
            if (changed == key || changed == null) value = prefs.getString(key, default) ?: default
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return Native250PreviewValue(value) { selected ->
        value = selected
        preferences.edit().putString(key, selected).apply()
    }
}

/** The same width, shape, padding, typography and colours as 2.5.0 dialogs. */
@Composable
internal fun Native250PreviewDialog(
    title: String,
    onDismiss: () -> Unit,
    showClose: Boolean = true,
    confirmation: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maximumHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).imePadding().heightIn(max = maximumHeight),
            shape = CsqttShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (showClose) IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "Закрыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
                if (confirmation != null) Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(CsqttSizes.ControlHeight),
                    shape = CsqttShapes.Pill,
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text(confirmation, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun Native250HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
}
