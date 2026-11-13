package com.csqtt.client.ui.v2.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.csqtt.client.*
import com.csqtt.client.ui.legacy.GOOBY_HELP
import com.csqtt.client.ui.legacy.ModeIndicator
import com.csqtt.client.ui.legacy.SRTP_HELP
import com.csqtt.client.ui.legacy.goobyIndicator
import com.csqtt.client.ui.legacy.goobyModeHelp
import com.csqtt.client.ui.legacy.hashModeHelp
import com.csqtt.client.ui.legacy.turnTransportHelp
import com.csqtt.client.ui.legacy.workModeHelp
import com.csqtt.client.ui.legacy.srtpModeHelp
import com.csqtt.client.ui.v2.components.*
import com.csqtt.client.ui.v2.design.V2Theme
import com.csqtt.client.ui.v2.design.V2Sizes
import com.csqtt.client.ui.v2.design.V2TextSizes
import com.csqtt.client.ui.v2.screens.connection.ProfileSwipeActions
import com.csqtt.client.ui.v2.dialogs.V2HashesDialog
import com.csqtt.client.ui.v2.dialogs.V2TurnSubnetsDialog
import com.csqtt.client.ui.v2.dialogs.V2AutoVkRiskDialog
import com.csqtt.client.ui.v2.dialogs.V2TcpTransportRiskDialog
import com.csqtt.client.ui.v2.screens.connection.V2ProfilePickerMenu
import com.csqtt.client.ui.v2.screens.connection.V2EditProfileModal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

private val draftWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
internal fun HappTunnelScreen(
    settingsStore: SettingsStore,
    screen: TunnelScreenSnapshot,
    version: String,
    onSettings: () -> Unit,
    onVkAuthRequested: () -> Unit,
) {
    val c = V2Theme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profile = screen.profile
    val snapshot = screen.connection
    val editingProfile = profile
    val names by settingsStore.profileNames.collectAsStateWithLifecycle(emptyMap())
    val occupied by settingsStore.occupiedProfiles.collectAsStateWithLifecycle(emptySet())
    val subscriptions by settingsStore.profileSubscriptions.collectAsStateWithLifecycle(emptyMap())
    val running by TunnelManager.running.collectAsStateWithLifecycle()
    val starting by TunnelManager.starting.collectAsStateWithLifecycle()
    val stopping by TunnelManager.stopping.collectAsStateWithLifecycle()
    val busy = running || starting || stopping
    val participant = snapshot.csqttLinkMode
    val rawLink = snapshot.csqttLink
    val link = remember(rawLink) { com.csqtt.client.ui.legacy.utils.parseCsqttLink(rawLink) }
    val ownerHashes = if (participant) link?.hashes.orEmpty() else emptyList()
    val manualHashGroup = screen.manualHashGroup
    val linkTokenActive = participant && !link?.vkAccessToken.isNullOrBlank()
    val hashes = screen.hashes
    val hashMode = screen.hashMode
    val authMode = screen.authMode
    val token = screen.token
    val srtp = screen.srtp
    val transport = screen.transport
    val goobyMode = screen.goobyMode
    val subnet = screen.subnet
    val ipLimit = screen.ipLimit
    val results = screen.hashResults
    val extraWorkers = snapshot.extraWorkers
    val workerMaximum = WorkerCountPolicy.defaultMaximum(extraWorkers && authMode != "auto_js")
    val revision = snapshot.revision
    var host by remember(profile, revision) { mutableStateOf(snapshot.peer) }
    var port by remember(profile, revision) { mutableStateOf(snapshot.serverPeerPort.toString()) }
    var password by remember(profile, revision) { mutableStateOf(snapshot.connectionPassword) }
    var workers by remember(profile, revision) { mutableIntStateOf(snapshot.workersPerHash) }
    var picker by remember { mutableStateOf(false) }
    var actionsExpanded by remember(profile) { mutableStateOf(false) }
    var confirmDelete by remember(profile) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var showHashes by remember { mutableStateOf(false) }
    var showSubnets by remember { mutableStateOf(false) }
    var showCaptcha by remember(profile) { mutableStateOf(false) }
    var pendingAutoJs by remember(profile) { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTcp by remember(profile) { mutableStateOf<(() -> Unit)?>(null) }
    val autoJsAcknowledged = screen.autoJsAcknowledged
    val tcpAcknowledged = screen.tcpAcknowledged
    val effectiveWorkers = WorkerCountPolicy.normalize(workers, workerMaximum)
    val draftTracker = remember(profile) { mutableStateOf<ProfileConnectionSnapshot?>(null) }
    SideEffect {
        val validPort = port.toIntOrNull()?.takeIf { it in 1..65535 }
        val changed = host.trim() != snapshot.peer || validPort != snapshot.serverPeerPort ||
            password != snapshot.connectionPassword || effectiveWorkers != snapshot.workersPerHash
        draftTracker.value = if (!busy && validPort != null && changed) {
            ProfileConnectionSnapshot(revision = revision, peer = host, serverPeerPort = validPort,
                connectionPassword = password, workersPerHash = effectiveWorkers)
        } else null
    }
    DisposableEffect(profile) {
        val slot = profile
        onDispose {
            val draft = draftTracker.value
            if (draft != null && draft.serverPeerPort in 1..65535) draftWrites.launch {
                settingsStore.saveProfileConnectionSettings(slot, draft.peer, draft.serverPeerPort, draft.connectionPassword, draft.workersPerHash, expectedRevision = draft.revision)
            }
        }
    }
    val pendingDraft = draftTracker.value
    LaunchedEffect(profile, pendingDraft) {
        val draft = pendingDraft ?: return@LaunchedEffect
        delay(350)
        settingsStore.saveProfileConnectionSettings(profile, draft.peer, draft.serverPeerPort, draft.connectionPassword,
            draft.workersPerHash, expectedRevision = draft.revision)
    }
    fun requestAutoJs(nextAuth: String, apply: () -> Unit) {
        if (shouldConfirmAutoJsMode(authMode, nextAuth, autoJsAcknowledged)) pendingAutoJs = apply else apply()
    }
    fun changeHash(label: String) {
        val mode = when(label) { "Авто API" -> "auto_api"; "Авто ВК" -> "auto_js"; else -> "manual" }
        requestAutoJs(vkAuthModeForHashMode(mode, authMode)) {
            scope.launch { settingsStore.saveVkHashMode(mode, forProfile = editingProfile) }
        }
    }

    val parts = hashes.split(Regex("[,\\s]+" )).filter(String::isNotBlank)
    val green = ModeIndicator(1f, c.success)
    val orange = ModeIndicator(1f, Color(0xFFF28C28))
    val red = ModeIndicator(1f, Color(0xFFE53935))
    val goobyAvailable = GoobyMode.isAvailable(transport)
    V2AdaptiveScreen(Modifier.fillMaxSize().padding(horizontal = V2Sizes.ScreenPadding), dense = true) {
        val layout = LocalV2AdaptiveMetrics.current
        val pill = RoundedCornerShape(50)
        V2TopBar(version = version, title = "Туннель", onSettings = onSettings)
        Column(Modifier.weight(1f).fillMaxWidth().testTag("tunnel-settings").imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(layout.gap)) {
            ProfileSwipeActions(
                expanded = actionsExpanded, enabled = !busy,
                onExpandedChange = { actionsExpanded = it },
                onEdit = { actionsExpanded = false; editing = profile },
                onDelete = { actionsExpanded = false; confirmDelete = true },
            ) { swipeModifier ->
                HappProfileHeader(
                    title = screen.name,
                    subtitle = if (host.isBlank()) "Сервер не настроен" else "$host:$port",
                    onSelect = { if (actionsExpanded) actionsExpanded = false else picker = true },
                    onEdit = { actionsExpanded = false; editing = profile },
                    enabled = !busy,
                    modifier = swipeModifier.testTag("tunnel-profile-header"),
                )
            }
            V2Card(modifier = Modifier.testTag("tunnel-card"), shape = RoundedCornerShape(28.dp), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp), color = c.surface,
                contentModifier = Modifier.heightIn(min = (layout.height - 56.dp - happProfileHeaderHeight() - layout.gap * 2).coerceAtLeast(0.dp)),
                verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HappField("Адрес сервера", host, { host = it }, Modifier.weight(1f), enabled = !busy, compact = true, shape = pill, floatingLabel = true, maxFieldHeight = 44.dp)
                    HappField("Порт PEER", port, { port = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), number = true, enabled = !busy, compact = true, shape = pill, floatingLabel = true, maxFieldHeight = 44.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                HappField("Пароль туннеля", password, { password = it }, Modifier.weight(1f), password = true, visibilityKey = profile to revision, enabled = !busy, compact = true, shape = pill, floatingLabel = true, maxFieldHeight = 44.dp)
                Box(Modifier.weight(1f)) {
                if (!linkTokenActive && authMode != "auto_js" && hashMode == "manual") {
                    V2PillButton("Настроить хеши", { showHashes = true }, modifier = Modifier.fillMaxWidth(), height = layout.fieldHeight.coerceAtMost(44.dp), horizontalPadding = 4.dp, enabled = !busy, style = V2PillStyle.Outline, shape = pill)
                } else {
                    V2PillButton(if (linkTokenActive) "Токен VK из ссылки" else if (token.isNotBlank()) "Аккаунт VK подключён" else "Войти в VK", onVkAuthRequested, modifier = Modifier.fillMaxWidth(), height = layout.fieldHeight.coerceAtMost(44.dp), horizontalPadding = 4.dp, enabled = !busy && !linkTokenActive, style = V2PillStyle.Accent, shape = pill, maxLines = 2)
                }
                }
                }
                if (!linkTokenActive) HappChoice("Режим хешей", when(hashMode) { "auto_api" -> "Авто API"; "auto_js" -> "Авто ВК"; else -> "Ручные хеши" }, listOf("Ручные хеши", "Авто API", "Авто ВК"), ::changeHash,
                    compact = true, shape = pill, indicators = mapOf("Ручные хеши" to green, "Авто API" to green, "Авто ВК" to orange), enabled = !busy,
                    help = hashModeHelp().second,
                    optionHelp = mapOf(
                        "Ручные хеши" to hashModeHelp(CsqttConstants.VkAutoHash.MODE_MANUAL).second,
                        "Авто API" to hashModeHelp(CsqttConstants.VkAutoHash.MODE_AUTO_API).second,
                        "Авто ВК" to hashModeHelp(CsqttConstants.VkAutoHash.MODE_AUTO_JS).second,
                    ))
                HappChoice("Режим кредов", when(authMode) { "auto_js" -> "Авто ВК"; "legacy" -> "Капча"; else -> "Авто" }, listOf("Авто", "Капча", "Авто ВК"), { label ->
                    val mode = when(label) { "Авто ВК" -> "auto_js"; "Капча" -> "legacy"; else -> "vkcalls" }
                    requestAutoJs(mode) { scope.launch { settingsStore.saveVkAuthMode(mode, forProfile = editingProfile) } }
                },
                    compact = true, shape = pill, indicators = mapOf("Авто" to green, "Капча" to red, "Авто ВК" to orange), enabled = !busy, help = workModeHelp().second,
                    optionHelp = mapOf(
                        "Авто" to workModeHelp(CsqttConstants.VkAuth.MODE_CALLS).second,
                        "Капча" to workModeHelp(CsqttConstants.VkAuth.MODE_CAPTCHA).second,
                        "Авто ВК" to workModeHelp(CsqttConstants.VkAuth.MODE_AUTO_JS).second,
                    ))
                if (authMode == "legacy") OutlinedButton(
                    onClick = { showCaptcha = true },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    border = BorderStroke(1.dp, c.outline),
                ) { Text("Настройки капчи", color = c.accent) }
                HappChoice("Режим SRTP", when(srtp) { "dtls-srtp" -> "DTLS-SRTP"; "wrap-srtp" -> "WRAP-SRTP"; else -> "Авто" }, listOf("Авто", "DTLS-SRTP", "WRAP-SRTP"), { label -> scope.launch { settingsStore.saveSrtpMode(when(label) { "DTLS-SRTP" -> "dtls-srtp"; "WRAP-SRTP" -> "wrap-srtp"; else -> "auto" }, forProfile = editingProfile) } },
                    compact = true, shape = pill, indicators = mapOf("Авто" to green, "DTLS-SRTP" to green, "WRAP-SRTP" to green.copy(progress = 0.5f)), enabled = !busy, help = SRTP_HELP, optionHelp = mapOf(
                        "Авто" to srtpModeHelp(CsqttConstants.SrtpMode.AUTO),
                        "DTLS-SRTP" to srtpModeHelp(CsqttConstants.SrtpMode.DTLS),
                        "WRAP-SRTP" to srtpModeHelp(CsqttConstants.SrtpMode.WRAP),
                    ))
                HappChoice("Транспорт", if(transport == "udp") "UDP" else "TCP", listOf("UDP", "TCP"), { label ->
                    val mode = if(label == "UDP") "udp" else CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
                    val apply = { scope.launch { settingsStore.saveTurnTransport(mode, forProfile = editingProfile) }; Unit }
                    if (shouldConfirmTcpTransport(transport, mode, tcpAcknowledged)) pendingTcp = apply else apply()
                },
                    compact = true, shape = pill, indicators = mapOf("UDP" to green, "TCP" to orange), enabled = !busy,
                    help = turnTransportHelp().second, optionHelp = mapOf(
                        "UDP" to turnTransportHelp(CsqttConstants.Tunnel.DEFAULT_TURN_TRANSPORT).second,
                        "TCP" to turnTransportHelp(CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS).second,
                    ))
                key(editingProfile, goobyAvailable, busy) {
                    HappChoice(
                        label = "Gooby",
                        value = (if (goobyAvailable) goobyMode else GoobyMode.OFF).label,
                        options = GoobyMode.entries.map { it.label },
                        onSelect = { label ->
                            val mode = GoobyMode.entries.first { it.label == label }
                            scope.launch { settingsStore.saveGoobyMode(mode, editingProfile) }
                        },
                        modifier = Modifier.alpha(if (goobyAvailable) 1f else 0.5f),
                        indicators = GoobyMode.entries.mapNotNull { mode ->
                            goobyIndicator(mode, c.success)?.let { mode.label to it }
                        }.toMap(),
                        help = GOOBY_HELP,
                        optionHelp = GoobyMode.entries.associate { it.label to goobyModeHelp(it) },
                        enabled = goobyAvailable && !busy,
                        compact = true,
                        shape = pill,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Потоки", Modifier.weight(1f), fontSize = V2TextSizes.Body)
                    HappHelp("Потоки", "Число параллельных TURN-потоков. Настройка относится к текущему профилю.")
                    V2PowerSlider(effectiveWorkers.toFloat(), maximum = workerMaximum.toFloat(), enabled = !busy,
                        onValueChange = { workers = WorkerCountPolicy.normalize(it.roundToInt(), workerMaximum) }, showHeader = false,
                        modifier = Modifier.weight(2f).padding(horizontal = 6.dp))
                    Surface(color = c.accentSoft, shape = RoundedCornerShape(10.dp)) { Text(effectiveWorkers.toString(), color = c.accent, fontSize = V2TextSizes.Body, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                }
                HappActionRow("TURN подсети", subnet.ifBlank { "Автоматически" }, { showSubnets = true }, compact = true)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить профиль?") },
        text = { Text("Профиль «${names[profile] ?: defaultProfileName(profile)}» будет удалён с устройства.") },
        confirmButton = { Button(enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = c.danger, contentColor = c.onDanger), onClick = {
            confirmDelete = false
            scope.launch { settingsStore.clearProfile(profile) }
        }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
    )
    if (picker) Dialog(onDismissRequest = { picker = false }) { V2ProfilePickerMenu(settingsStore, profile, names, occupied, subscriptions, busy, { picker = false }, { editing = it }) }
    editing?.let { slot -> Dialog(onDismissRequest = { editing = null }) { V2EditProfileModal(settingsStore, slot, names[slot] ?: defaultProfileName(slot), isNew = false, onSaved = { editing = null }, onDismiss = { editing = null }) } }
    if (showHashes) V2HashesDialog(parts.getOrElse(0){""}, parts.getOrElse(1){""}, parts.getOrElse(2){""}, parts.getOrElse(3){""}, parts.getOrElse(4){""}, parts.getOrElse(5){""},
        VkHashValidationCodec.decode(results), { scope.launch { settingsStore.saveVkHashCheckResults(VkHashValidationCodec.encode(it)) } },
        { list -> VkHashValidator.check(context, list, settingsStore.selectedFingerprint.first(), settingsStore.activeClientIds.first()) },
        onSave = { group, hashes ->
            scope.launch {
                settingsStore.saveManualHashes(group, hashes.filter(String::isNotBlank).distinct().joinToString(","), editingProfile)
            }
        },
        onDismiss = { showHashes = false },
        ownerHashes = ownerHashes,
        selectedGroup = if (ownerHashes.isNotEmpty()) manualHashGroup else ManualHashGroup.Own,
    )
    if (showSubnets) V2TurnSubnetsDialog(subnet, ipLimit, { scope.launch { settingsStore.saveSelectedTurnSubnet(it, forProfile = editingProfile) } }, { scope.launch { settingsStore.saveTurnSubnetIpLimit(it, forProfile = editingProfile) } }, { showSubnets = false })
    if (showCaptcha) HappCaptchaDialog(settingsStore, profile, busy) { showCaptcha = false }
    pendingAutoJs?.let { apply -> V2AutoVkRiskDialog(true,
        onAcknowledge = { pendingAutoJs = null; apply() },
        onDoNotRemind = { scope.launch { settingsStore.saveAutoJsRiskAcknowledged(true) }; pendingAutoJs = null; apply() }) }
    pendingTcp?.let { apply -> V2TcpTransportRiskDialog(
        onAcknowledge = { pendingTcp = null; apply() },
        onDoNotRemind = { scope.launch { settingsStore.saveTcpTransportRiskAcknowledged(true) }; pendingTcp = null; apply() }) }
}
