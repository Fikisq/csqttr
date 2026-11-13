@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.csqtt.client.ui.v2.dialogs

import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.ButtonDefaults
import com.csqtt.client.ui.v2.components.HappHelp
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.csqtt.client.ManualHashGroup
import com.csqtt.client.VkHashValidationCodec
import com.csqtt.client.VkHashValidationStatus
import com.csqtt.client.ui.legacy.utils.normalizeVkHashInput
import com.csqtt.client.ui.v2.design.V2Theme
import com.csqtt.client.ui.v2.components.V2PillButton
import com.csqtt.client.ui.v2.components.V2PillStyle
import kotlinx.coroutines.launch

@Composable
internal fun V2HashesDialog(
    hash1: String,
    hash2: String,
    hash3: String,
    hash4: String,
    hash5: String,
    hash6: String,
    validationResults: Map<String, VkHashValidationStatus>,
    onValidationResultsChange: (Map<String, VkHashValidationStatus>) -> Unit,
    onCheck: suspend (List<String>) -> Map<String, VkHashValidationStatus>,
    onAutoSave: ((String, String, String, String, String, String) -> Unit)? = null,
    onSave: (ManualHashGroup, List<String>) -> Unit,
    onDismiss: () -> Unit,
    ownerHashes: List<String> = emptyList(),
    selectedGroup: ManualHashGroup = ManualHashGroup.Own,
) {
    val colors = V2Theme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var h1 by rememberSaveable { mutableStateOf(hash1) }
    var h2 by rememberSaveable { mutableStateOf(hash2) }
    var h3 by rememberSaveable { mutableStateOf(hash3) }
    var h4 by rememberSaveable { mutableStateOf(hash4) }
    var h5 by rememberSaveable { mutableStateOf(hash5) }
    var h6 by rememberSaveable { mutableStateOf(hash6) }
    var results by remember(validationResults) { mutableStateOf(validationResults) }
    var isChecking by remember { mutableStateOf(false) }
    var unavailableCount by remember { mutableIntStateOf(0) }

    val fields = listOf(h1, h2, h3, h4, h5, h6)
    val hashes = fields.map(::normalizeVkHashInput)

    var group by rememberSaveable(selectedGroup) { mutableStateOf(selectedGroup) }
    val showOwnerHashes = group == ManualHashGroup.Owner
    val visibleFields = if (showOwnerHashes) List(6) { ownerHashes.getOrElse(it) { "" } } else fields
    val activeHashes = (if (showOwnerHashes) ownerHashes else hashes).filter { it.length >= 16 }.distinct()

    fun updateHash(index: Int, raw: String) {
        val input = raw.filterNot(Char::isWhitespace)
        if (input == fields[index]) return
        val previous = hashes[index]
        if (normalizeVkHashInput(input) != previous && results.containsKey(previous)) {
            val updatedResults = VkHashValidationCodec.invalidate(results, previous)
            results = updatedResults
            onValidationResultsChange(updatedResults)
        }
        unavailableCount = 0
        when (index) {
            0 -> h1 = input
            1 -> h2 = input
            2 -> h3 = input
            3 -> h4 = input
            4 -> h5 = input
            5 -> h6 = input
        }
        onAutoSave?.let { save ->
            val values = listOf(h1, h2, h3, h4, h5, h6).map(::normalizeVkHashInput)
            save(values[0], values[1], values[2], values[3], values[4], values[5])
        }
    }


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun animateDismiss(onDone: () -> Unit = onDismiss) {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDone()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ручные хеши", Modifier.weight(1f), color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                HappHelp("Ручные хеши", "Вставьте ссылки на звонки VK или готовые хеши. Доступно шесть полей. Проверка отмечает доступность каждого хеша; она не проверяет VPS или пароль туннеля.")
                IconButton(onClick = { animateDismiss() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Закрыть", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in ManualHashGroup.entries) {
                    V2PillButton(
                        text = option.label,
                        onClick = {
                            group = option
                            unavailableCount = 0
                        },
                        enabled = !isChecking && (option != ManualHashGroup.Owner || ownerHashes.isNotEmpty()),
                        modifier = Modifier.weight(1f),
                        style = if (group == option) V2PillStyle.Accent else V2PillStyle.Outline,
                        height = 40.dp,
                    )
                }
            }
            Text("Ссылки на звонки VK или готовые хеши", color = colors.textSecondary, fontSize = 12.sp)
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleFields.forEachIndexed { index, value ->
                    val hash = if (showOwnerHashes) value else hashes[index]
                    val short = hash.isNotBlank() && hash.length < 16
                    val status = results[hash]
                    val outline = when (status) {
                        VkHashValidationStatus.Valid -> colors.success
                        VkHashValidationStatus.Invalid -> colors.error
                        null -> if (short) colors.error else colors.outline
                    }
                    Column {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.field,
                            border = BorderStroke(1.dp, outline),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(50.dp).padding(start = 10.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = colors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                BasicTextField(
                                    value = value,
                                    onValueChange = { updateHash(index, it) },
                                    enabled = !isChecking,
                                    readOnly = showOwnerHashes,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).semantics {
                                        when (status) {
                                            VkHashValidationStatus.Valid -> stateDescription = "Хеш активен"
                                            VkHashValidationStatus.Invalid -> stateDescription = "Хеш недействителен"
                                            null -> Unit
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    decorationBox = { input ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (value.isEmpty()) {
                                                Text(
                                                    "Ссылка VK или хеш · от 16 знаков",
                                                    color = colors.textMuted,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            input()
                                        }
                                    },
                                )
                                if (!showOwnerHashes) {
                                    IconButton(
                                        onClick = {
                                            context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                                                ?.toString()?.takeIf { it.isNotBlank() }?.let { updateHash(index, it) }
                                        },
                                        enabled = !isChecking,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.ContentPaste,
                                            "Вставить хеш ${index + 1}",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    if (value.isNotEmpty()) {
                                        IconButton(
                                            onClick = { updateHash(index, "") },
                                            enabled = !isChecking,
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Очистить хеш ${index + 1}",
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (short) Text("Минимум 16 символов", color = colors.error, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
                    }
                }
            }
            if (unavailableCount > 0) Text("Не удалось проверить: $unavailableCount", color = colors.textSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    isChecking = true
                    unavailableCount = 0
                    scope.launch {
                        try {
                            val checked = onCheck(activeHashes)
                            results = results + checked
                            onValidationResultsChange(results)
                            unavailableCount = activeHashes.size - checked.size
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { unavailableCount = activeHashes.size }
                        finally { isChecking = false }
                    }
                }, enabled = !isChecking && activeHashes.isNotEmpty(), modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.outline)) {
                    Text(if (isChecking) "Проверяем…" else "Проверить (${activeHashes.size})", fontSize = 13.sp)
                }
                Button(onClick = { onSave(group, hashes); animateDismiss() }, enabled = !isChecking,
                    modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White)) {
                    Text("Сохранить", fontSize = 13.sp)
                }
            }
        }
    }
}
