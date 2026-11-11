package com.csqtt.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.csqtt.client.AppRoutingPresets
import com.csqtt.client.ui.components.CsqttLoadingState
import com.csqtt.client.ui.components.CsqttSegmentedControl
import com.csqtt.client.ui.components.CsqttSettingRow
import com.csqtt.client.ui.design.CsqttShapes

/** Selection semantics from 59.9; the dialog, controls and rows retain the classic theme. */
@Composable
internal fun ClassicAppsRoutingDialog(
    apps: List<AppItem>, loading: Boolean, isWhitelist: Boolean,
    blacklist: Set<String>, whitelist: Set<String>, presetsUsed: Set<String>,
    allAppsMode: Boolean?, showSystemApps: Boolean, onShowSystemAppsChange: (Boolean) -> Unit,
    onClose: (Set<String>, Set<String>, Boolean, Set<String>, Boolean) -> Unit,
) {
    var black by remember { mutableStateOf(blacklist) }
    var white by remember { mutableStateOf(whitelist) }
    var used by remember { mutableStateOf(presetsUsed) }
    var all by rememberSaveable { mutableStateOf(allAppsMode ?: (!isWhitelist && blacklist.isEmpty())) }
    var whiteMode by rememberSaveable { mutableStateOf(isWhitelist) }
    var pendingPreset by remember { mutableStateOf<Boolean?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val selection = if (whiteMode) white else black

    LaunchedEffect(pendingPreset, loading, apps) {
        val mode = pendingPreset ?: return@LaunchedEffect
        if (loading) return@LaunchedEffect
        val installed = apps.map { it.packageName }.toSet()
        if (mode) white = AppRoutingPresets.initial(white, "white" in used, installed, true)
        else black = AppRoutingPresets.initial(black, "black" in used, installed, false)
        used = used + if (mode) "white" else "black"
        pendingPreset = null
    }

    val filtered = remember(apps, search, showSystemApps, selection) {
        val matching = apps.filter {
            (!it.isSystem || showSystemApps || it.packageName in selection) &&
                (search.isBlank() || it.name.contains(search, true) || it.packageName.contains(search, true))
        }
        val byPackage = matching.associateBy { it.packageName }
        AppRoutingPresets.orderedPackages(matching.map { it.packageName }, selection).mapNotNull(byPackage::get)
    }
    fun close() = onClose(black, white, if (all) false else whiteMode, used, all)

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = CsqttShapes.Dialog, color = appSectionCardColor(),
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Список приложений", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = ::close, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть список приложений")
                    }
                }
                CsqttSegmentedControl(
                    options = listOf("all" to "Все с VPN", "black" to "Без VPN", "white" to "С VPN"),
                    selected = if (all) "all" else if (whiteMode) "white" else "black",
                    onSelected = { mode ->
                        listState.requestScrollToItem(0)
                        all = mode == "all"
                        if (all) pendingPreset = null else {
                            whiteMode = mode == "white"
                            pendingPreset = if (mode in used) null else whiteMode
                        }
                    }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = search, onValueChange = {
                    if (it != search) listState.requestScrollToItem(0)
                    search = it
                }, placeholder = { Text("Поиск приложения") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true, shape = CsqttShapes.Control, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                if (!all) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            listState.requestScrollToItem(0)
                            pendingPreset = null
                            val merged = AppRoutingPresets.merge(selection, apps.map { it.packageName }.toSet(), whiteMode)
                            if (whiteMode) white = merged else black = merged
                            used = used + if (whiteMode) "white" else "black"
                        }, enabled = !loading, shape = CsqttShapes.Pill, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Готовый список", maxLines = 1) }
                        OutlinedButton(onClick = {
                            listState.requestScrollToItem(0)
                            pendingPreset = null
                            if (whiteMode) white = emptySet() else black = emptySet()
                            used = used + if (whiteMode) "white" else "black"
                        }, enabled = !loading, shape = CsqttShapes.Pill, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Очистить список", maxLines = 1) }
                    }
                    Text("Выбрано: ${selection.size} " + if (whiteMode) "(в туннеле)" else "(исключено)",
                        style = MaterialTheme.typography.labelLarge)
                    CsqttSettingRow(title = "Показывать системные приложения", checked = showSystemApps,
                        onCheckedChange = { listState.requestScrollToItem(0); onShowSystemAppsChange(it) })
                }
                when {
                    all -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Все приложения используют VPN")
                    }
                    loading && apps.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CsqttLoadingState(label = "Загрузка приложений")
                    }
                    filtered.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Приложения не найдены")
                    }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filtered, key = { it.packageName }, contentType = { "application" }) { app ->
                            val selected = app.packageName in selection
                            AppRow(app, selected, onClick = {
                                listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                used = used + if (whiteMode) "white" else "black"
                                val next = if (selected) selection - app.packageName else selection + app.packageName
                                if (whiteMode) white = next else black = next
                            })
                        }
                    }
                }
            }
        }
    }
}
