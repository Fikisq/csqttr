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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.csqtt.client.*
import com.csqtt.client.ui.legacy.tunnel.ModeIndicator
import com.csqtt.client.ui.v2.components.*
import com.csqtt.client.ui.v2.design.V2Theme
import com.csqtt.client.ui.v2.design.V2Sizes
import com.csqtt.client.ui.v2.design.V2TextSizes
import com.csqtt.client.ui.v2.screens.connection.ProfileSwipeActions
import com.csqtt.client.ui.v2.dialogs.V2HashesDialog
import com.csqtt.client.ui.v2.dialogs.V2TurnSubnetsDialog
import com.csqtt.client.ui.v2.dialogs.V2VkTokenRevokeDialog
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
fun HappTunnelScreen(settingsStore: SettingsStore, version: String, onSettings: () -> Unit, onVkAuthRequested: () -> Unit) {
    val c = V2Theme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profile by settingsStore.activeProfile.collectAsStateWithLifecycle(0)
    val editingProfile = profile
    val names by settingsStore.profileNames.collectAsStateWithLifecycle(emptyMap())
    val occupied by settingsStore.occupiedProfiles.collectAsStateWithLifecycle(emptySet())
    val subscriptions by settingsStore.profileSubscriptions.collectAsStateWithLifecycle(emptyMap())
    val running by TunnelManager.running.collectAsStateWithLifecycle()
    val starting by TunnelManager.starting.collectAsStateWithLifecycle()
    val stopping by TunnelManager.stopping.collectAsStateWithLifecycle()
    val busy = running || starting || stopping
    val participant by settingsStore.csqttLinkMode.collectAsStateWithLifecycle(false)
    val rawLink by settingsStore.csqttLink.collectAsStateWithLifecycle("")
    val link = remember(rawLink) { com.csqtt.client.ui.legacy.utils.parseCsqttLink(rawLink) }
    val ownerHashes = if (participant) link?.hashes.orEmpty() else emptyList()
    val removedOwner by settingsStore.linkHashesRemoved.collectAsStateWithLifecycle(emptySet())
    val linkTokenActive = participant && !link?.vkAccessToken.isNullOrBlank()
    val hashes by settingsStore.vkHashes.collectAsStateWithLifecycle("")
    val hashMode by settingsStore.vkHashMode.collectAsStateWithLifecycle("manual")
    val authMode by settingsStore.vkAuthMode.collectAsStateWithLifecycle("vkcalls")
    val token by settingsStore.vkAccessToken.collectAsStateWithLifecycle("")
    val srtp by settingsStore.srtpMode.collectAsStateWithLifecycle("auto")
    val transport by settingsStore.turnTransport.collectAsStateWithLifecycle("udp")
    val subnet by settingsStore.selectedTurnSubnet.collectAsStateWithLifecycle("")
    val ipLimit by settingsStore.turnSubnetIpLimit.collectAsStateWithLifecycle("ALL")
    val results by settingsStore.vkHashCheckResults.collectAsStateWithLifecycle("{}")
    val extraWorkers by settingsStore.extraWorkers.collectAsStateWithLifecycle(false)
    val workerMaximum = WorkerCountPolicy.defaultMaximum(extraWorkers && authMode != "auto_js")
    var loaded by remember(profile) { mutableStateOf(false) }
    var host by remember(profile) { mutableStateOf("") }
    var port by remember(profile) { mutableStateOf("46000") }
    var password by remember(profile) { mutableStateOf("") }
    var workers by remember(profile) { mutableIntStateOf(10) }
    var revision by remember(profile) { mutableLongStateOf(0L) }
    var picker by remember { mutableStateOf(false) }
    var actionsExpanded by remember(profile) { mutableStateOf(false) }
    var confirmDelete by remember(profile) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var showHashes by remember { mutableStateOf(false) }
    var showSubnets by remember { mutableStateOf(false) }
    var showCaptcha by remember(profile) { mutableStateOf(false) }
    var showVkSession by remember(profile) { mutableStateOf(false) }
    var pendingAutoJs by remember(profile) { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTcp by remember(profile) { mutableStateOf<(() -> Unit)?>(null) }
    val autoJsAcknowledged by settingsStore.autoJsRiskAcknowledged.collectAsStateWithLifecycle(false)
    val tcpAcknowledged by settingsStore.tcpTransportRiskAcknowledged.collectAsStateWithLifecycle(false)
    val effectiveWorkers = WorkerCountPolicy.normalize(workers, workerMaximum)
    val draftTracker = remember(profile) { mutableStateOf<ProfileConnectionSnapshot?>(null) }
    SideEffect {
        draftTracker.value = if (loaded && !busy) ProfileConnectionSnapshot(revision = revision, peer = host, serverPeerPort = port.toIntOrNull() ?: 0, connectionPassword = password, workersPerHash = effectiveWorkers) else null
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
    LaunchedEffect(profile) {
        val saved = settingsStore.readProfileConnectionSettings(profile)
        host = saved.peer; port = saved.serverPeerPort.toString(); password = saved.connectionPassword
        workers = saved.workersPerHash; revision = saved.revision; loaded = true
    }
    LaunchedEffect(profile, loaded, revision, host, port, password, effectiveWorkers) {
        if (!loaded || busy) return@LaunchedEffect
        delay(350)
        val validPort = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return@LaunchedEffect
        settingsStore.saveProfileConnectionSettings(profile, host, validPort, password, effectiveWorkers, expectedRevision = revision)
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
    LaunchedEffect(profile, authMode, extraWorkers, ownerHashes) {
        if (authMode == "auto_js" && ownerHashes.isNotEmpty()) settingsStore.saveVkAuthMode("vkcalls", forProfile = profile)
        if (authMode == "auto_js" && extraWorkers) settingsStore.saveExtraWorkers(false, forProfile = profile)
    }
    val parts = hashes.split(Regex("[,\\s]+" )).filter(String::isNotBlank)
    val green = c.success
    val yellow = Color(0xFFFACC15)
    val hashIndicators = mapOf(
        "Ручные хеши" to ModeIndicator(0.78f, yellow),
        "Авто API" to ModeIndicator(1f, green),
        "Авто ВК" to ModeIndicator(1f, yellow),
    )
    val credentialIndicators = mapOf(
        "Авто" to ModeIndicator(0.78f, green),
        "Капча" to ModeIndicator(0.30f, c.danger),
        "Авто ВК" to ModeIndicator(1f, yellow),
    )
    val srtpIndicators = mapOf(
        "Авто" to ModeIndicator(1f, green),
        "DTLS-SRTP" to ModeIndicator(1f, green),
        "WRAP-SRTP" to ModeIndicator(0.5f, c.warning),
    )
    val transportIndicators = mapOf(
        "UDP" to ModeIndicator(1f, green),
        "TCP" to ModeIndicator(0.7f, yellow),
    )
    V2AdaptiveScreen(Modifier.fillMaxSize().padding(horizontal = V2Sizes.ScreenPadding), dense = transport != "udp") {
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
                    title = names[profile] ?: defaultProfileName(profile),
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
                    HappField("Адрес сервера", host, { host = it }, Modifier.weight(1f), enabled = loaded && !busy, compact = true, shape = pill, floatingLabel = true, maxFieldHeight = 44.dp)
                    HappField("Порт PEER", port, { port = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), number = true, enabled = loaded && !busy, compact = true, shape = pill, floatingLabel = true, maxFieldHeight = 44.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                HappField("Пароль туннеля", password, { password = it }, Modifier.weight(1f), password = true, visibilityKey = profile, enabled = loaded && !busy, compact = true, shape = pill, floatingLabel = true, maxFieldHeight = 44.dp)
                Box(Modifier.weight(1f)) {
                if (!linkTokenActive && ownerHashes.isEmpty() && authMode != "auto_js" && hashMode == "manual" || ownerHashes.isNotEmpty()) {
                    V2PillButton("Настроить хеши", { showHashes = true }, modifier = Modifier.fillMaxWidth(), height = layout.fieldHeight.coerceAtMost(44.dp), horizontalPadding = 4.dp, enabled = !busy, style = V2PillStyle.Outline, shape = pill)
                } else {
                    V2PillButton(if (linkTokenActive) "Токен VK из ссылки" else if (token.isNotBlank()) "Аккаунт VK подключён" else "Войти в VK", { if (token.isNotBlank()) showVkSession = true else onVkAuthRequested() }, modifier = Modifier.fillMaxWidth(), height = layout.fieldHeight.coerceAtMost(44.dp), horizontalPadding = 4.dp, enabled = !busy && !linkTokenActive, style = V2PillStyle.Accent, shape = pill, maxLines = 2)
                }
                }
                }
                if (ownerHashes.isNotEmpty()) Text("Хеши владельца: ${ownerHashes.count { it !in removedOwner }} · свои: ${parts.size}", color = c.accent, fontSize = 12.sp)
                if (ownerHashes.isEmpty() && !linkTokenActive) HappChoice("Режим хешей", when(hashMode) { "auto_api" -> "Авто API"; "auto_js" -> "Авто ВК"; else -> "Ручные хеши" }, listOf("Ручные хеши", "Авто API", "Авто ВК"), ::changeHash,
                    compact = true, shape = pill, indicators = hashIndicators, enabled = !busy,
                    help = "Ручные хеши — шесть хешей звонков без входа в VK. Авто API — получение через API VK. Авто ВК — получение через авторизованный аккаунт VK.")
                HappChoice("Режим кредов", when(authMode) { "auto_js" -> "Авто ВК"; "legacy" -> "Капча"; else -> "Авто" }, if (ownerHashes.isNotEmpty()) listOf("Авто", "Капча") else listOf("Авто", "Капча", "Авто ВК"), { label ->
                    val mode = when(label) { "Авто ВК" -> "auto_js"; "Капча" -> "legacy"; else -> "vkcalls" }
                    requestAutoJs(mode) { scope.launch { settingsStore.saveVkAuthMode(mode, forProfile = editingProfile) } }
                },
                    compact = true, shape = pill, indicators = credentialIndicators, enabled = !busy, help = "Авто — гостевые TURN-креды VK Calls. Капча — браузерный способ с возможной капчей. Авто ВК — креды авторизованного аккаунта.")
                if (authMode == "legacy") OutlinedButton(
                    onClick = { showCaptcha = true },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    border = BorderStroke(1.dp, c.outline),
                ) { Text("Настройки капчи", color = c.accent) }
                HappChoice("Режим SRTP", when(srtp) { "dtls-srtp" -> "DTLS-SRTP"; "wrap-srtp" -> "WRAP-SRTP"; else -> "Авто" }, listOf("Авто", "DTLS-SRTP", "WRAP-SRTP"), { label -> scope.launch { settingsStore.saveSrtpMode(when(label) { "DTLS-SRTP" -> "dtls-srtp"; "WRAP-SRTP" -> "wrap-srtp"; else -> "auto" }, forProfile = editingProfile) } },
                    compact = true, shape = pill, indicators = srtpIndicators, enabled = !busy, help = "Авто — DTLS-SRTP с переходом к WRAP-SRTP. DTLS-SRTP — настоящее рукопожатие. WRAP-SRTP — SRTP-обёртка без DTLS-рукопожатия.")
                HappChoice("Транспорт", if(transport == "udp") "UDP" else "TCP", listOf("UDP", "TCP"), { label ->
                    val mode = if(label == "UDP") "udp" else CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
                    val apply = { scope.launch { settingsStore.saveTurnTransport(mode, forProfile = editingProfile) }; Unit }
                    if (shouldConfirmTcpTransport(transport, mode, tcpAcknowledged)) pendingTcp = apply else apply()
                },
                    compact = true, shape = pill, indicators = transportIndicators, enabled = !busy,
                    help = "UDP — основной транспорт TURN. TCP/TLS — альтернатива, если UDP недоступен.")
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
    editing?.let { slot -> Dialog(onDismissRequest = { editing = null }) { V2EditProfileModal(settingsStore, slot, names[slot] ?: defaultProfileName(slot), isNew = false, onSaved = { loaded = false; scope.launch { val saved = settingsStore.readProfileConnectionSettings(profile); host = saved.peer; port = saved.serverPeerPort.toString(); password = saved.connectionPassword; workers = saved.workersPerHash; revision = saved.revision; loaded = true } }, onDismiss = { editing = null }) } }
    if (showHashes) V2HashesDialog(parts.getOrElse(0){""}, parts.getOrElse(1){""}, parts.getOrElse(2){""}, parts.getOrElse(3){""}, parts.getOrElse(4){""}, parts.getOrElse(5){""},
        VkHashValidationCodec.decode(results), { scope.launch { settingsStore.saveVkHashCheckResults(VkHashValidationCodec.encode(it)) } },
        { list -> VkHashValidator.check(context, list, settingsStore.selectedFingerprint.first(), settingsStore.activeClientIds.first()) },
        onSave = { a,b,d,e,f,g -> scope.launch { settingsStore.saveVkHashesOnly(listOf(a,b,d,e,f,g).filter(String::isNotBlank).distinct().joinToString(",")) } }, onDismiss = { showHashes = false },
        ownerHashes = ownerHashes, removedOwnerHashes = removedOwner,
        onRemovedOwnerHashesChange = { removed -> scope.launch { settingsStore.saveLinkHashesRemoved(removed, forProfile = editingProfile) } })
    if (showSubnets) V2TurnSubnetsDialog(subnet, ipLimit, { scope.launch { settingsStore.saveSelectedTurnSubnet(it, forProfile = editingProfile) } }, { scope.launch { settingsStore.saveTurnSubnetIpLimit(it, forProfile = editingProfile) } }, { showSubnets = false })
    if (showCaptcha) HappCaptchaDialog(settingsStore, profile, busy) { showCaptcha = false }
    if (showVkSession) V2VkTokenRevokeDialog(
        onCancel = { showVkSession = false },
        onCopyToken = {
            context.getSystemService(android.content.ClipboardManager::class.java)
                ?.setPrimaryClip(android.content.ClipData.newPlainText("VK token", token))
            context.showRaisedToast("Токен VK скопирован", android.widget.Toast.LENGTH_SHORT)
        },
        onRevokeToken = { showVkSession = false; scope.launch { settingsStore.clearVkAccessToken() } },
    )
    pendingAutoJs?.let { apply -> V2AutoVkRiskDialog(true,
        onAcknowledge = { pendingAutoJs = null; apply() },
        onDoNotRemind = { scope.launch { settingsStore.saveAutoJsRiskAcknowledged(true) }; pendingAutoJs = null; apply() }) }
    pendingTcp?.let { apply -> V2TcpTransportRiskDialog(
        onAcknowledge = { pendingTcp = null; apply() },
        onDoNotRemind = { scope.launch { settingsStore.saveTcpTransportRiskAcknowledged(true) }; pendingTcp = null; apply() }) }
}
