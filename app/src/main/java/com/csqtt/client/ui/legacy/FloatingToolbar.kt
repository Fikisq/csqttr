package com.csqtt.client.ui.legacy

import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import com.csqtt.client.SettingsStore
import com.csqtt.client.defaultProfileName
import com.csqtt.client.BatterySaver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csqtt.client.R

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.verticalScroll
import com.csqtt.client.CsqttConstants
import com.csqtt.client.TunnelManager
import com.csqtt.client.ui.legacy.components.CsqttSegmentedControl
import com.csqtt.client.ui.legacy.components.CsqttSettingRow
import com.csqtt.client.ui.legacy.design.CsqttShapes
import com.csqtt.client.ui.legacy.design.CsqttSizes
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.csqtt.client.ui.legacy.dialogs.ImportExportDialog
import com.csqtt.client.ui.legacy.dialogs.QrScannerDialog
import com.csqtt.client.ui.legacy.dialogs.SupportDeveloperDialog
import com.csqtt.client.ui.legacy.dialogs.TurnSubnetsDialog
import com.csqtt.client.BuildConfig
import com.csqtt.client.ui.v2.design.LocalV2Colors
import com.csqtt.client.ui.v2.design.V2ClassicLightColors
import com.csqtt.client.ui.v2.design.V2ClassicOriginalColors
import com.csqtt.client.ui.v2.dialogs.V2ExtraWorkersInfoDialog
import com.csqtt.client.ui.v2.dialogs.V2ResourceSheet
import com.csqtt.client.ui.v2.dialogs.V2VersionSheet
import com.csqtt.client.ui.v2.screens.settings.V2AboutScreen

private enum class UsageRole { Owner, Participant }

@Composable
fun FloatingToolbar(
    settingsStore: SettingsStore,
    csqttLinkMode: Boolean,
    activeProfile: Int,
    onActiveProfileChange: (Int) -> Unit,
    onAddProfile: (Int) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    activeFingerprint: String,
    onFingerprintChange: (String) -> Unit,
    resourceMonitorOpen: Boolean,
    onResourceMonitorOpenChange: (Boolean) -> Unit,
    floatingHudEnabled: Boolean,
    onToggleFloatingHud: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedRole = if (csqttLinkMode) UsageRole.Participant else UsageRole.Owner
    val vkAuthMode by settingsStore.vkAuthMode.collectAsStateWithLifecycle(
        initialValue = CsqttConstants.VkAuth.MODE_CALLS,
    )
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val tunnelStarting by TunnelManager.starting.collectAsStateWithLifecycle()
    val tunnelStopping by TunnelManager.stopping.collectAsStateWithLifecycle()
    val tunnelPaused by TunnelManager.autoPausedForWifi.collectAsStateWithLifecycle()
    val tunnelBusy = tunnelRunning || tunnelStarting || tunnelStopping || tunnelPaused
    val extraWorkers by settingsStore.extraWorkers.collectAsStateWithLifecycle(false)
    val selectedTurnSubnet by settingsStore.selectedTurnSubnet.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val turnSubnetIpLimit by settingsStore.turnSubnetIpLimit.collectAsStateWithLifecycle(
        initialValue = "ALL",
    )
    val uiDesign by settingsStore.uiDesign.collectAsStateWithLifecycle(
        initialValue = CsqttConstants.Theme.DESIGN_CLASSIC,
    )
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showSupportDeveloperDialog by rememberSaveable { mutableStateOf(false) }
    var showImportExportDialog by rememberSaveable { mutableStateOf(false) }
    val autoPauseOnWifi by settingsStore.autoPauseOnWifi.collectAsStateWithLifecycle(false)
    val uiScalePercent by settingsStore.uiScalePercent.collectAsStateWithLifecycle(100)
    var showExtraWorkersInfo by rememberSaveable { mutableStateOf(false) }
    var showLogsInfo by rememberSaveable { mutableStateOf(false) }
    var showVersionSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutScreen by rememberSaveable { mutableStateOf(false) }
    var showWifiNetworkListDialog by rememberSaveable { mutableStateOf(false) }
    var showBatterySaverInfo by rememberSaveable { mutableStateOf(false) }
    val batterySaverMode by settingsStore.batterySaverMode.collectAsStateWithLifecycle(
        initialValue = BatterySaver.DEFAULT_MODE,
    )
    val batterySaverPercent by settingsStore.batterySaverPercent.collectAsStateWithLifecycle(
        initialValue = BatterySaver.DEFAULT_PERCENT,
    )
    var showTurnSubnetsDialog by rememberSaveable { mutableStateOf(false) }
    val profileNames by settingsStore.profileNames.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )
    val participantShowLogs by settingsStore.participantShowLogs.collectAsStateWithLifecycle(
        initialValue = SettingsStore.DEFAULT_PARTICIPANT_SHOW_LOGS,
    )
    val occupiedProfiles by settingsStore.occupiedProfiles.collectAsStateWithLifecycle(emptySet())
    var editingProfileIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var deletingProfileIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingProfileName by rememberSaveable { mutableStateOf("") }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val settingsIconTint = if (isDarkTheme) Color.White else Color.Black

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showQrScanner = true
        } else {
            Toast.makeText(context, "Требуется разрешение на камеру для сканирования QR-кода", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        IconButton(
            onClick = { showSupportDeveloperDialog = true },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_donate_heart),
                contentDescription = "Поддержать разработчика",
                modifier = Modifier.size(24.dp),
                tint = settingsIconTint,
            )
        }

        if (!csqttLinkMode) {
            IconButton(
                onClick = { showImportExportDialog = true },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_import_export),
                    contentDescription = "Импорт и экспорт конфигурации",
                    modifier = Modifier.size(24.dp),
                    tint = settingsIconTint,
                )
            }
        }

        IconButton(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    showQrScanner = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_qr_scan),
                contentDescription = "Сканировать QR-код",
                modifier = Modifier.size(24.dp),
                tint = settingsIconTint,
            )
        }

        IconButton(
            onClick = { isExpanded = true },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_gear),
                contentDescription = stringResource(R.string.quick_settings),
                modifier = Modifier.size(24.dp),
                tint = settingsIconTint,
            )
        }
    }

    if (showSupportDeveloperDialog) {
        SupportDeveloperDialog(
            onDismissRequest = { showSupportDeveloperDialog = false }
        )
    }

    if (showImportExportDialog) {
        ImportExportDialog(
            settingsStore = settingsStore,
            onDismissRequest = { showImportExportDialog = false },
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismissRequest = { showQrScanner = false },
            onQrCodeScanned = { rawLink ->
                val parsed = com.csqtt.client.ui.legacy.utils.parseCsqttLink(rawLink)
                if (parsed != null) {
                    showQrScanner = false
                    scope.launch {
                        settingsStore.saveCsqttParticipantLink(rawLink.trim())
                    }
                    Toast.makeText(context, "Ссылка подключена из QR-кода ✓ Режим участника включён", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "QR-код не содержит ссылку CSQTT", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (isExpanded) {
            Dialog(
                onDismissRequest = { isExpanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = CsqttShapes.Dialog,
                    color = appSectionCardColor(),
                    border = BorderStroke(1.dp, appSectionCardBorderColor()),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Настройки",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    CompactDropdownSetting(
                        title = "Профиль конфигурации",
                        selectedKey = activeProfile.toString(),
                        options = occupiedProfiles.sorted().map { profile ->
                            profile.toString() to (profileNames[profile] ?: defaultProfileName(profile))
                        },
                        enabled = true,
                        unselectedLabel = "Не выбран",
                        triggerModifier = Modifier.testTag("classic-profile-picker"),
                        menuAction = "Добавить профиль" to {
                            scope.launch {
                                val slot = settingsStore.findFirstFreeProfile()
                                if (slot != null) {
                                    isExpanded = false
                                    onAddProfile(slot)
                                } else {
                                    Toast.makeText(context, "Нет свободных слотов для профиля", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSelected = { profile ->
                            profile.toIntOrNull()?.let(onActiveProfileChange)
                        },
                        onItemEdit = { key ->
                            val p = key.toIntOrNull() ?: 0
                            editingProfileIndex = p
                            editingProfileName = profileNames[p] ?: defaultProfileName(p)
                        },
                    )

                    Text(
                        "Ваша роль использования",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    CsqttSegmentedControl(
                        options = listOf(
                            UsageRole.Participant to "Участник",
                            UsageRole.Owner to "Владелец",
                        ),
                        selected = selectedRole,
                        onSelected = { role ->
                            val participant = role == UsageRole.Participant
                            if (participant != csqttLinkMode) {
                                scope.launch { settingsStore.saveUiOwnerMode(!participant) }
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(CsqttSizes.CompactControlHeight),
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    CsqttSettingRow(
                        title = "Экстра потоки",
                        description = "До ${CsqttConstants.Tunnel.MAX_WORKERS} потоков вместо ${CsqttConstants.Tunnel.DEFAULT_MAX_WORKERS}",
                        checked = extraWorkers,
                        enabled = !tunnelRunning && vkAuthMode != CsqttConstants.VkAuth.MODE_AUTO_JS,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsStore.saveExtraWorkers(enabled, forProfile = activeProfile) }
                        },
                        onInfoClick = { showExtraWorkersInfo = true },
                    )

                    val wifiStatus = if (autoPauseOnWifi) "Включена" else "Выключена"
                    QuickSettingsActionRow("Автопауза Wi-Fi", detail = wifiStatus) {
                        showWifiNetworkListDialog = true
                    }

                    CsqttSettingRow(
                        title = "Показывать вкладку «Логи»",
                        checked = participantShowLogs,
                        onCheckedChange = { checked ->
                            scope.launch { settingsStore.saveParticipantShowLogs(checked, forProfile = activeProfile) }
                        },
                        onInfoClick = { showLogsInfo = true },
                        titleMaxLines = 2,
                    )

                    CompactDropdownSetting(
                        title = "Масштаб интерфейса",
                        selectedKey = uiScalePercent.toString(),
                        options = SettingsStore.UI_SCALE_CHOICES.map { it.toString() to "$it%" },
                        enabled = true,
                        onSelected = { value -> scope.launch { settingsStore.saveUiScalePercent(value.toInt()) } },
                    )

                    CompactDropdownSetting(
                        title = "Дизайн",
                        selectedKey = uiDesign,
                        options = listOf(
                            CsqttConstants.Theme.DESIGN_V2 to stringResource(R.string.design_csqttr_253),
                            CsqttConstants.Theme.DESIGN_CLASSIC to stringResource(R.string.design_classic_csqtt_250),
                            CsqttConstants.Theme.DESIGN_OLD to stringResource(R.string.design_historical),
                        ),
                        enabled = true,
                        onSelected = { design -> scope.launch { settingsStore.saveUiDesign(design) } },
                    )

                    CompactDropdownSetting(
                        title = "Оформление",
                        selectedKey = currentTheme,
                        options = listOf(
                            CsqttConstants.Theme.MODE_SYSTEM to "Системное",
                            CsqttConstants.Theme.MODE_LIGHT to "Светлое",
                            CsqttConstants.Theme.MODE_DARK to "Тёмное",
                        ),
                        enabled = true,
                        onSelected = onThemeChange,
                    )

                    CompactDropdownSetting(
                        title = "Отпечаток TLS",
                        selectedKey = activeFingerprint,
                        options = listOf(
                            "chrome" to "Chrome",
                            "safari" to "Safari",
                            "firefox" to "Firefox",
                            "edge" to "Edge",
                            "opera" to "Opera",
                        ),
                        enabled = true,
                        onSelected = onFingerprintChange,
                    )

                    QuickSettingsActionRow("Импорт / экспорт") { showImportExportDialog = true }
                    QuickSettingsActionRow("Монитор ресурсов") { onResourceMonitorOpenChange(true) }
                    QuickSettingsActionRow("Версия и обновления") { showVersionSheet = true }
                    QuickSettingsActionRow("О приложении") { showAboutScreen = true }

                    CompactDropdownSetting(
                        title = BatterySaver.TITLE,
                        selectedKey = batterySaverMode,
                        options = BatterySaver.MODES,
                        enabled = true,
                        onSelected = { mode -> scope.launch { settingsStore.saveBatterySaverMode(mode) } },
                        onTitleInfo = { showBatterySaverInfo = true },
                    )

                    CompactDropdownSetting(
                        title = BatterySaver.LEVEL_TITLE,
                        selectedKey = batterySaverPercent.toString(),
                        options = (BatterySaver.PERCENT_CHOICES + batterySaverPercent).distinct().sorted()
                            .map { percent -> percent.toString() to "≤ $percent %" },
                        enabled = batterySaverMode == BatterySaver.MODE_LEVEL,
                        onSelected = { key ->
                            key.toIntOrNull()?.let { percent -> scope.launch { settingsStore.saveBatterySaverPercent(percent) } }
                        },
                    )

                    if (!csqttLinkMode) {
                        QuickSettingsOutlineButton(
                            text = "Выбор TURN подсетей",
                            onClick = { showTurnSubnetsDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showBatterySaverInfo) {
        BatterySaverInfoDialog(onDismiss = { showBatterySaverInfo = false })
    }

    if (showExtraWorkersInfo) V2ExtraWorkersInfoDialog { showExtraWorkersInfo = false }

    if (showLogsInfo) {
        AlertDialog(
            onDismissRequest = { showLogsInfo = false },
            title = { Text("Вкладка «Логи»") },
            text = { Text("Показывает или скрывает вкладку для текущего профиля. Запись событий продолжается и при скрытой вкладке.") },
            confirmButton = { TextButton(onClick = { showLogsInfo = false }) { Text("Понятно") } },
        )
    }

    if (showVersionSheet || showAboutScreen || resourceMonitorOpen) {
        val colors = if (isDarkTheme) V2ClassicOriginalColors else V2ClassicLightColors
        val version = "V ${BuildConfig.VERSION_NAME.removePrefix("v").substringBefore('-')}"
        CompositionLocalProvider(LocalV2Colors provides colors) {
            if (showVersionSheet) V2VersionSheet(version = version, onDismiss = { showVersionSheet = false })
            if (resourceMonitorOpen) {
                V2ResourceSheet(
                    floatingHudEnabled = floatingHudEnabled,
                    onToggleFloatingHud = onToggleFloatingHud,
                    onDismiss = { onResourceMonitorOpenChange(false) },
                )
            }
            if (showAboutScreen) {
                Dialog(
                    onDismissRequest = { showAboutScreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Surface(Modifier.fillMaxSize()) {
                        V2AboutScreen(version = version, onBack = { showAboutScreen = false })
                    }
                }
            }
        }
    }

    if (showWifiNetworkListDialog) {
        WifiNetworkListDialog(
            settingsStore = settingsStore,
            onDismiss = { showWifiNetworkListDialog = false },
        )
    }

    if (showTurnSubnetsDialog) {
        TurnSubnetsDialog(
            selectedSubnetId = selectedTurnSubnet,
            turnSubnetIpLimit = turnSubnetIpLimit,
            onSelectSubnet = { id ->
                scope.launch { settingsStore.saveSelectedTurnSubnet(id) }
            },
            onSelectIpLimit = { limit ->
                scope.launch { settingsStore.saveTurnSubnetIpLimit(limit) }
            },
            onDismiss = { showTurnSubnetsDialog = false },
        )
    }

    editingProfileIndex?.let { p ->
        Dialog(
            onDismissRequest = { editingProfileIndex = null },
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Название профиля",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { editingProfileIndex = null },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editingProfileName,
                        onValueChange = { editingProfileName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CsqttShapes.Control,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { editingProfileName = "" },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Очистить название",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )

                    Button(
                        onClick = {
                            val targetProfile = p
                            val targetName = editingProfileName
                            editingProfileIndex = null
                            scope.launch {
                                settingsStore.saveProfileName(targetProfile, targetName)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CsqttSizes.ControlHeight),
                        shape = CsqttShapes.Control,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = "Сохранить",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (p in occupiedProfiles) {
                        TextButton(
                            onClick = { deletingProfileIndex = p },
                            enabled = !tunnelBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Удалить профиль") }
                    }
                }
            }
        }
    }
    deletingProfileIndex?.let { profile ->
        AlertDialog(
            onDismissRequest = { deletingProfileIndex = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text("Удалить профиль?") },
            text = { Text("Профиль «${profileNames[profile] ?: defaultProfileName(profile)}» будет удалён с устройства.") },
            confirmButton = {
                TextButton(
                    enabled = !tunnelBusy && profile in occupiedProfiles,
                    onClick = {
                        deletingProfileIndex = null
                        editingProfileIndex = null
                        scope.launch { settingsStore.clearProfile(profile) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deletingProfileIndex = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun QuickSettingsActionRow(title: String, detail: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = CsqttSizes.CompactControlHeight)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickSettingsOutlineButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(CsqttSizes.ControlHeight),
        shape = CsqttShapes.Pill,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
