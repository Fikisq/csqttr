package com.csqtt.client.ui.v2.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csqtt.client.ui.v2.design.V2TextSizes
import com.csqtt.client.ui.v2.design.V2Theme

@Composable
internal fun happProfileHeaderHeight(): Dp =
    maxOf(60.dp, with(LocalDensity.current) { 40.sp.toDp() } + 12.dp)

@Composable
internal fun HappProfileHeader(
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    editDescription: String = "Изменить профиль",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = V2Theme.colors
    V2Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Row(Modifier.height(happProfileHeaderHeight()), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = enabled, role = Role.Button, onClick = onSelect),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(title, color = colors.textPrimary, fontSize = V2TextSizes.Body,
                        lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = colors.textSecondary, fontSize = V2TextSizes.Secondary,
                        lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = colors.textSecondary)
            }
            IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Edit, editDescription, Modifier.size(20.dp), tint = colors.textPrimary)
            }
            actions()
        }
    }
}
