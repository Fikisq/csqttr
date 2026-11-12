package com.csqtt.client.ui.v2.screens.routing

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.testTag
import com.csqtt.client.ui.v2.components.V2AdaptiveScreen
import com.csqtt.client.ui.v2.components.LocalV2AdaptiveMetrics
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csqtt.client.R
import com.csqtt.client.SettingsStore
import com.csqtt.client.TunnelManager
import com.csqtt.client.ui.legacy.AppItem
import com.csqtt.client.ui.v2.components.V2Card
import com.csqtt.client.ui.v2.components.V2PillButton
import com.csqtt.client.ui.v2.components.V2PillStyle
import com.csqtt.client.ui.v2.components.V2TopBar
import com.csqtt.client.ui.v2.design.V2Shapes
import com.csqtt.client.ui.v2.design.V2Sizes
import com.csqtt.client.ui.v2.design.V2Spacing
import com.csqtt.client.ui.v2.design.V2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object V2AppCache {
    @Volatile
    var cachedList: List<AppItem>? = null
}

@Composable
fun V2RoutingScreen(
    settingsStore: SettingsStore,
    version: String,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onVersionClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val colors = V2Theme.colors

    val serverRoutingActiveLive by TunnelManager.serverRoutingActive.collectAsStateWithLifecycle()
    val isServerRouting = serverRoutingActiveLive
    val showSystemApps by settingsStore.showSystemApps.collectAsStateWithLifecycle(false)

    val savedExcluded by settingsStore.excludedApps.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedExcludedApps ?: "",
    )
    val savedWhitelist by settingsStore.whitelistApps.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedWhitelistApps ?: "",
    )
    val isWhitelist by settingsStore.isWhitelist.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedIsWhitelist ?: false,
    )
    val templateDomainsEnabled by settingsStore.templateDomainsEnabled.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedTemplateDomainsEnabled ?: false,
    )
    val domainIsWhitelist by settingsStore.domainIsWhitelist.collectAsStateWithLifecycle(initialValue = false)
    val directDomainsBlackStr by settingsStore.directDomainsBlack.collectAsStateWithLifecycle(
        initialValue = SettingsStore.cachedDirectDomains ?: "",
    )
    val directDomainsWhiteStr by settingsStore.directDomainsWhite.collectAsStateWithLifecycle(initialValue = "")
    val ipIsWhitelist by settingsStore.ipIsWhitelist.collectAsStateWithLifecycle(initialValue = false)
    val directIpsBlackStr by settingsStore.directIpsBlack.collectAsStateWithLifecycle(initialValue = "")
    val directIpsWhiteStr by settingsStore.directIpsWhite.collectAsStateWithLifecycle(initialValue = "")

    val templateDomains = remember {
        val text = runCatching {
            context.assets.open("domains.txt").bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        parseDomainsFromText(text)
    }

    val directDomainsList = remember(directDomainsBlackStr, directDomainsWhiteStr, domainIsWhitelist) {
        parseDomainsFromText(if (domainIsWhitelist) directDomainsWhiteStr else directDomainsBlackStr)
    }
    val directIpsList = remember(directIpsBlackStr, directIpsWhiteStr, ipIsWhitelist) {
        parseIpsFromText(if (ipIsWhitelist) directIpsWhiteStr else directIpsBlackStr)
    }

    val persistedBlacklist = remember(savedExcluded) {
        savedExcluded.split(',').filter(String::isNotBlank).toSet()
    }
    val persistedWhitelist = remember(savedWhitelist) {
        savedWhitelist.split(',').filter(String::isNotBlank).toSet()
    }

    var appsList by remember { mutableStateOf(V2AppCache.cachedList.orEmpty()) }
    var isAppsLoading by remember { mutableStateOf(false) }
    var isMigrationReady by remember { mutableStateOf(false) }

    var showAppsDialog by rememberSaveable { mutableStateOf(false) }
    var showDomainsDialog by rememberSaveable { mutableStateOf(false) }
    var showIpsDialog by rememberSaveable { mutableStateOf(false) }
    var showRulesTransferDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { settingsStore.migrateLegacyWhitelistMode() }
        isMigrationReady = true
    }

    // The app list with icons is only needed when the apps dialog is opened. Loading it lazily
    // keeps the routing tab light even on devices with hundreds of installed applications.
    LaunchedEffect(showAppsDialog) {
        if (!showAppsDialog) return@LaunchedEffect
        V2AppCache.cachedList?.let {
            appsList = it
            isAppsLoading = false
            return@LaunchedEffect
        }
        isAppsLoading = true
        val loadedApps = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val apps = packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { app ->
                    app.packageName != context.packageName &&
                        !app.packageName.contains("vkontakte") &&
                        !app.packageName.contains("vk.calls")
                }
                .map { app ->
                    AppItem(
                        name = app.loadLabel(packageManager).toString(),
                        packageName = app.packageName,
                        icon = runCatching {
                            // Icons are shown at 40dp; decoding them at full intrinsic size
                            // (often 192-432 px) for every installed app wasted ~100 MB.
                            app.loadIcon(packageManager)
                                .toBitmap(width = 96, height = 96)
                                .asImageBitmap()
                        }.getOrNull(),
                        isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .sortedWith(compareBy<AppItem> { it.isSystem }.thenBy { it.name.lowercase() })
                .toList()
            // Release the temporary full-size drawable bitmaps right away instead of
            // keeping the transient spike in the native heap until the next GC.
            Runtime.getRuntime().gc()
            apps
        }
        V2AppCache.cachedList = loadedApps
        appsList = loadedApps
        isAppsLoading = false
    }

    LaunchedEffect(isServerRouting) {
        if (isServerRouting) {
            showAppsDialog = false
            showDomainsDialog = false
            showIpsDialog = false
            showRulesTransferDialog = false
        }
    }

    V2AdaptiveScreen(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = V2Sizes.ScreenPadding),
    ) {
        val layout = LocalV2AdaptiveMetrics.current
        V2TopBar(
            version = version,
            title = "Роутинг",
            onSettings = onSettings,
            onVersionClick = onVersionClick,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = layout.gap),
            verticalArrangement = Arrangement.spacedBy(layout.gap),
        ) {
            if (isServerRouting) {
                V2Card(color = colors.surface) {
                    Text(
                        text = "Маршрутизация синхронизирована с сервером",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = V2Spacing.Xl),
                    )
                }
            } else {
                V2RoutingCard(
                    tag = "APP",
                    title = "Маршрутизация приложений",
                    countLabel = appsCountLabel(
                        isWhitelist = isWhitelist,
                        blacklistSize = persistedBlacklist.size,
                        whitelistSize = persistedWhitelist.size,
                    ),
                    action = "Список приложений",
                    icon = rememberVectorPainter(Icons.Outlined.Apps),
                    onClick = { showAppsDialog = true },
                )
                V2RoutingCard(
                    tag = "DM",
                    title = "Маршрутизация доменов",
                    countLabel = domainsCountLabel(
                        isWhitelist = domainIsWhitelist,
                        listSize = directDomainsList.size,
                        templateDomainsEnabled = templateDomainsEnabled,
                        templateSize = templateDomains.size,
                        customCount = directDomainsList.count { !templateDomains.contains(it) },
                    ),
                    action = "Список доменов",
                    icon = rememberVectorPainter(Icons.Outlined.Language),
                    onClick = { showDomainsDialog = true },
                )
                V2RoutingCard(
                    tag = "IP",
                    title = "Маршрутизация адресов",
                    countLabel = if (ipIsWhitelist) {
                        "Белый список: ${directIpsList.size} в туннеле"
                    } else {
                        "Чёрный список: ${directIpsList.size} исключено"
                    },
                    action = "Список адресов",
                    icon = painterResource(R.drawable.ic_ip),
                    onClick = { showIpsDialog = true },
                )
                V2RoutingCard(
                    tag = "transfer",
                    title = "Импорт и экспорт правил",
                    countLabel = "Резервное копирование и перенос настроек",
                    action = "Импорт / экспорт",
                    icon = rememberVectorPainter(Icons.Default.SwapVert),
                    onClick = { showRulesTransferDialog = true },
                )
            }
        }
    }

    if (showAppsDialog) {
        V2AppsRoutingDialog(
            appsList = appsList,
            isAppsLoading = isAppsLoading || !isMigrationReady,
            isWhitelist = isWhitelist,
            blacklist = persistedBlacklist,
            whitelist = persistedWhitelist,
            showSystemApps = showSystemApps,
            onShowSystemAppsChange = { enabled -> scope.launch { settingsStore.saveShowSystemApps(enabled) } },
            onClose = { black, white, modeWhite ->
                showAppsDialog = false
                val modeChanged = modeWhite != isWhitelist
                val blackChanged = black != persistedBlacklist
                val whiteChanged = white != persistedWhitelist
                val blackOrderChanged = black.toList() != persistedBlacklist.toList()
                val whiteOrderChanged = white.toList() != persistedWhitelist.toList()
                if (modeChanged || blackOrderChanged || whiteOrderChanged) {
                    scope.launch {
                        if (modeChanged) settingsStore.saveIsWhitelist(modeWhite)
                        if (blackOrderChanged) settingsStore.saveExcludedApps(black.joinToString(","))
                        if (whiteOrderChanged) settingsStore.saveWhitelistApps(white.joinToString(","))
                        if (modeChanged || blackChanged || whiteChanged) TunnelManager.reloadVpn()
                    }
                }
            },
        )
    }

    if (showDomainsDialog) {
        V2DomainsRoutingDialog(
            isWhitelist = domainIsWhitelist,
            blackStr = directDomainsBlackStr,
            whiteStr = directDomainsWhiteStr,
            templateDomainsEnabled = templateDomainsEnabled,
            templateDomains = templateDomains,
            onClose = { blackFormatted, whiteFormatted, modeWhite, templateEnabled ->
                showDomainsDialog = false
                scope.launch {
                    settingsStore.saveDomainIsWhitelist(modeWhite)
                    settingsStore.saveTemplateDomainsEnabled(templateEnabled)
                    settingsStore.saveDirectDomainsBlack(blackFormatted)
                    settingsStore.saveDirectDomainsWhite(whiteFormatted)
                    TunnelManager.pushLocalRouting(context)
                }
            },
        )
    }

    if (showIpsDialog) {
        V2IpsRoutingDialog(
            isWhitelist = ipIsWhitelist,
            blackStr = directIpsBlackStr,
            whiteStr = directIpsWhiteStr,
            onClose = { blackFormatted, whiteFormatted, modeWhite ->
                showIpsDialog = false
                scope.launch {
                    settingsStore.saveIpIsWhitelist(modeWhite)
                    settingsStore.saveIpRoutingEnabled(true)
                    settingsStore.saveDirectIpsBlack(blackFormatted)
                    settingsStore.saveDirectIpsWhite(whiteFormatted)
                    TunnelManager.pushLocalRouting(context)
                }
            },
        )
    }

    if (showRulesTransferDialog) {
        V2RulesTransferDialog(
            settingsStore = settingsStore,
            isWhitelist = isWhitelist,
            blacklistSelection = persistedBlacklist,
            whitelistSelection = persistedWhitelist,
            domainIsWhitelist = domainIsWhitelist,
            templateDomainsEnabled = templateDomainsEnabled,
            templateDomains = templateDomains,
            directDomainsBlackStr = directDomainsBlackStr,
            directDomainsWhiteStr = directDomainsWhiteStr,
            ipIsWhitelist = ipIsWhitelist,
            directIpsBlackStr = directIpsBlackStr,
            directIpsWhiteStr = directIpsWhiteStr,
            onClose = { showRulesTransferDialog = false },
        )
    }
}

private fun appsCountLabel(isWhitelist: Boolean, blacklistSize: Int, whitelistSize: Int): String =
    if (isWhitelist) "Белый список: $whitelistSize в туннеле"
    else "Чёрный список: $blacklistSize исключено"

private fun domainsCountLabel(
    isWhitelist: Boolean,
    listSize: Int,
    templateDomainsEnabled: Boolean,
    templateSize: Int,
    customCount: Int,
): String = when {
    isWhitelist -> "Белый список: $listSize в туннеле"
    listSize == 0 -> "Чёрный список: 0 доменов исключено"
    templateDomainsEnabled && customCount > 0 -> "Исключено: $templateSize готовых + $customCount своих"
    templateDomainsEnabled -> "Исключено: $listSize (Готовый список RU)"
    else -> "Чёрный список: $listSize исключено"
}

@Composable
private fun V2RoutingCard(
    tag: String,
    title: String,
    countLabel: String,
    action: String,
    icon: Painter,
    onClick: () -> Unit,
) {
    val colors = routingColors()
    val layout = LocalV2AdaptiveMetrics.current
    V2Card(
        modifier = Modifier.testTag("routing-card-$tag"),
        color = colors.surface,
        contentPadding = PaddingValues(V2Spacing.Lg),
        verticalArrangement = Arrangement.spacedBy(V2Spacing.Md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(V2Spacing.Xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        V2PillButton(
            text = action,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.outline, V2Shapes.Pill),
            iconPainter = icon,
            style = V2PillStyle.Outline,
            height = layout.buttonHeight.coerceAtLeast(48.dp),
            shape = V2Shapes.Pill,
            maxLines = 2,
        )
    }
}

// --- routing text rules helpers (transferred from legacy ExceptionsTab) ---

private val domainPattern = Regex("^[a-z0-9\u0400-\u04ff]([a-z0-9\u0400-\u04ff\\-]{0,61}[a-z0-9\u0400-\u04ff])?$")
private val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

internal fun cleanDomain(raw: String): String {
    val trimmed = raw.trim().lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('/')
        .substringBefore(':')
        .substringBefore('?')
        .substringBefore('#')
    return trimmed.trim(',', ';', ' ', '\t', '\n', '\r', '.', '(', ')', '[', ']', '"', '\'')
}

internal fun isValidDomain(domain: String): Boolean {
    if (domain.length < 3 || domain.length > 253) return false
    val parts = domain.split('.')
    if (parts.size < 2) return false
    val tld = parts.last()
    if (tld.length < 2 || !tld.any { it.isLetter() }) return false
    return parts.all { domainPattern.matches(it) }
}

internal fun parseDomainsFromText(text: String): List<String> {
    return text.split(Regex("[,;\\s\\n\\r]+"))
        .map { cleanDomain(it) }
        .filter { isValidDomain(it) }
        .distinct()
        .take(1024)
}

internal fun isValidIpv4(ip: String): Boolean {
    val parts = ip.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        val num = part.toIntOrNull() ?: return false
        num in 0..255 && (part == "0" || !part.startsWith("0"))
    }
}

internal fun isValidIpRule(raw: String): Boolean {
    val item = raw.trim()
    if (item.isEmpty()) return false
    if (item.contains('/')) {
        val parts = item.split('/')
        if (parts.size != 2) return false
        val prefix = parts[1].toIntOrNull() ?: return false
        return prefix in 0..32 && isValidIpv4(parts[0].trim())
    }
    if (item.contains('-')) {
        val parts = item.split('-')
        if (parts.size != 2) return false
        return isValidIpv4(parts[0].trim()) && isValidIpv4(parts[1].trim())
    }
    return isValidIpv4(item)
}

internal fun cleanIpRule(raw: String): String {
    val item = raw.trim()
    if (item.contains('-')) {
        val parts = item.split('-')
        if (parts.size == 2) {
            return "${parts[0].trim()}-${parts[1].trim()}"
        }
    }
    return item
}

internal fun parseIpsFromText(text: String): List<String> {
    return text.split(Regex("[,;\\s\\n\\r]+"))
        .map { cleanIpRule(it) }
        .filter { isValidIpRule(it) }
        .distinct()
        .take(1024)
}

internal fun isValidPackage(pkg: String): Boolean {
    if (pkg.length < 3 || pkg.length > 255) return false
    val parts = pkg.split('.')
    if (parts.size < 2) return false
    return packagePattern.matches(pkg)
}

internal fun parseAppExportFromText(text: String): Pair<Set<String>, Set<String>>? {
    val lines = text.lines()
    var currentSection: String? = null
    val black = mutableSetOf<String>()
    val white = mutableSetOf<String>()
    var headerFound = false

    fun extractPkgs(line: String): List<String> {
        val clean = line.substringBefore('#').substringBefore("//").trim()
        if (clean.isBlank()) return emptyList()
        return clean.split(Regex("[,;\\s\\t]+"))
            .map { it.trim().trim(',', ';', '"', '\'').substringBefore(':').substringBefore('=').trim() }
            .filter { isValidPackage(it) }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val lower = trimmed.lowercase()
        if (lower.startsWith("чёрный-список:") || lower.startsWith("черный-список:") ||
            lower.startsWith("чёрный список:") || lower.startsWith("черный список:") ||
            lower.startsWith("blacklist:") || lower.startsWith("black:")
        ) {
            headerFound = true
            currentSection = "black"
            val rest = trimmed.substringAfter(':').trim()
            if (rest.isNotEmpty()) black.addAll(extractPkgs(rest))
        } else if (lower.startsWith("белый-список:") || lower.startsWith("белый список:") ||
            lower.startsWith("whitelist:") || lower.startsWith("white:")
        ) {
            headerFound = true
            currentSection = "white"
            val rest = trimmed.substringAfter(':').trim()
            if (rest.isNotEmpty()) white.addAll(extractPkgs(rest))
        } else {
            if (currentSection == "black") {
                black.addAll(extractPkgs(trimmed))
            } else if (currentSection == "white") {
                white.addAll(extractPkgs(trimmed))
            }
        }
    }
    return if (headerFound) Pair(black, white) else null
}
