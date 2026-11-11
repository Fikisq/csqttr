package com.csqtt.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.csqtt.client.ui.design.CsqttShapes
import com.csqtt.client.ui.design.CsqttSpacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.luminance

@Immutable
internal data class AppItem(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean,
)

private val routingIconCache = android.util.LruCache<String, ImageBitmap>(96)

@Composable
internal fun AppRow(
    app: AppItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val icon by produceState(initialValue = app.icon ?: routingIconCache.get(app.packageName), key1 = app.packageName) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.packageManager.getApplicationIcon(app.packageName)
                        .toBitmap(width = 96, height = 96).asImageBitmap()
                }.getOrNull()
            }
            loaded?.let { routingIconCache.put(app.packageName, it) }
            value = loaded
        }
    }
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f

    val surfaceColor = if (isSelected) {
        colors.primaryContainer.copy(alpha = if (isDark) 0.65f else 0.85f)
    } else {
        colors.surface.copy(alpha = if (isDark) 0.50f else 0.65f)
    }

    val borderColor = if (isSelected) {
        colors.primary.copy(alpha = 0.50f)
    } else {
        colors.outlineVariant.copy(alpha = if (isDark) 0.28f else 0.35f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
        shape = CsqttShapes.Card,
        color = surfaceColor,
        contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CsqttSpacing.Md, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(CsqttSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon!!,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp).clip(CsqttShapes.Small),
                )
            } else {
                Box(modifier = Modifier.size(42.dp).background(colors.surfaceVariant, CsqttShapes.Small))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
