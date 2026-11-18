package com.csqtt.client.ui.legacy.dialogs

import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import com.csqtt.client.ui.legacy.appSectionCardBorderColor
import com.csqtt.client.ui.legacy.appSectionCardColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.csqtt.client.ManualHashGroup
import com.csqtt.client.VkHashValidationCodec
import com.csqtt.client.VkHashValidationStatus
import com.csqtt.client.ui.legacy.utils.normalizeVkHashInput
import com.csqtt.client.ui.legacy.design.CsqttShapes
import com.csqtt.client.ui.legacy.design.CsqttSizes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HashesDialog(
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
    var h1 by rememberSaveable { mutableStateOf(hash1) }
    var h2 by rememberSaveable { mutableStateOf(hash2) }
    var h3 by rememberSaveable { mutableStateOf(hash3) }
    var h4 by rememberSaveable { mutableStateOf(hash4) }
    var h5 by rememberSaveable { mutableStateOf(hash5) }
    var h6 by rememberSaveable { mutableStateOf(hash6) }
    var results by remember(validationResults) { mutableStateOf(validationResults) }

    var isChecking by remember { mutableStateOf(false) }
    var unavailableCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fields = listOf(h1, h2, h3, h4, h5, h6)
    val hashes = fields.map(::normalizeVkHashInput)

    var group by rememberSaveable(selectedGroup) { mutableStateOf(selectedGroup) }
    val showOwnerHashes = group == ManualHashGroup.Owner
    val visibleFields = if (showOwnerHashes) List(6) { ownerHashes.getOrElse(it) { "" } } else fields
    val activeHashes = (if (showOwnerHashes) ownerHashes else hashes).filter { it.length >= 16 }.distinct()
    val validBorder = if (appSectionCardColor().luminance() < 0.5f) Color(0xFF66BB6A) else Color(0xFF2E7D32)

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = CsqttShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "VK Хеши",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Text(
                    text = "Больше хешей — выше лимит потоков и лучшее распределение нагрузки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in ManualHashGroup.entries) {
                        val select = {
                            group = option
                            unavailableCount = 0
                        }
                        val enabled = !isChecking && (option != ManualHashGroup.Owner || ownerHashes.isNotEmpty())
                        if (group == option) {
                            Button(onClick = select, enabled = enabled, modifier = Modifier.weight(1f), shape = CsqttShapes.Pill) {
                                Text(option.label)
                            }
                        } else {
                            OutlinedButton(onClick = select, enabled = enabled, modifier = Modifier.weight(1f), shape = CsqttShapes.Pill) {
                                Text(option.label)
                            }
                        }
                    }
                }

                visibleFields.forEachIndexed { idx, value ->
                    val hash = if (showOwnerHashes) value else hashes[idx]
                    val isShort = hash.isNotBlank() && hash.length < 16
                    val status = results[hash]
                    val isInvalid = status == VkHashValidationStatus.Invalid
                    val fieldColors = if (status == VkHashValidationStatus.Valid) {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = validBorder,
                            unfocusedBorderColor = validBorder,
                        )
                    } else {
                        OutlinedTextFieldDefaults.colors()
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { updateHash(idx, it) },
                        readOnly = showOwnerHashes,
                        label = { Text("Хеш ${idx + 1}", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        placeholder = {
                            Text(
                                "Ссылка звонка или хеш",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        enabled = !isChecking,
                        isError = isShort || isInvalid,
                        supportingText = if (isShort) {
                            { Text("Хеш ${idx + 1} — короткий (мин. 16)", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        trailingIcon = if (showOwnerHashes) null else { {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        val clipText = context.getSystemService(ClipboardManager::class.java)
                                            ?.primaryClip
                                            ?.takeIf { it.itemCount > 0 }
                                            ?.getItemAt(0)
                                            ?.coerceToText(context)
                                            ?.toString()
                                        if (!clipText.isNullOrBlank()) {
                                            updateHash(idx, clipText)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = "Вставить из буфера в хеш ${idx + 1}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { updateHash(idx, "") },
                                    enabled = value.isNotEmpty(),
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить хеш ${idx + 1}",
                                        tint = MaterialTheme.colorScheme.error.copy(
                                            alpha = if (value.isNotEmpty()) 1f else 0.38f,
                                        ),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } },
                        modifier = Modifier.fillMaxWidth().semantics {
                            when (status) {
                                VkHashValidationStatus.Valid -> stateDescription = "Хеш активен"
                                VkHashValidationStatus.Invalid -> stateDescription = "Хеш недействителен"
                                null -> Unit
                            }
                        },
                        shape = CsqttShapes.Control,
                        colors = fieldColors,
                    )
                    }

                OutlinedButton(
                    onClick = {
                        isChecking = true
                        unavailableCount = 0
                        scope.launch {
                            val checked = onCheck(activeHashes)
                            val updatedResults = results + checked
                            results = updatedResults
                            onValidationResultsChange(updatedResults)
                            unavailableCount = activeHashes.size - checked.size
                            isChecking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(CsqttSizes.ControlHeight),
                    enabled = !isChecking && activeHashes.isNotEmpty(),
                    shape = CsqttShapes.Pill,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        if (isChecking) "Проверяем..." else "Проверить актив",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (unavailableCount > 0) {
                    Text(
                        "Не удалось проверить: $unavailableCount. Поля оставлены без статуса.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                }

                Button(
                    onClick = {
                        onSave(group, hashes)
                    },
                    modifier = Modifier.fillMaxWidth().height(CsqttSizes.ControlHeight),
                    shape = CsqttShapes.Pill,
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text("Сохранить", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
