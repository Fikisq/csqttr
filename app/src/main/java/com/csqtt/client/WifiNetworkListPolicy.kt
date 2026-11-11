package com.csqtt.client

import org.json.JSONObject

internal enum class WifiNetworkListMode(val storedValue: String, val label: String) {
    ALL("all", "На любом Wi-Fi"),
    BLACKLIST("blacklist", "Только в выбранных сетях"),
    WHITELIST("whitelist", "Во всех, кроме выбранных");

    companion object {
        fun fromStored(value: String?): WifiNetworkListMode =
            entries.firstOrNull { it.storedValue == value } ?: ALL
    }
}

internal fun normalizeWifiSsid(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.equals("<unknown ssid>", ignoreCase = true)) return null
    val unquoted = if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
        value.substring(1, value.length - 1)
    } else {
        value
    }
    return unquoted.trim().takeIf { it.isNotEmpty() }
}

internal fun decodeWifiSsids(raw: String?): Set<String> =
    raw.orEmpty()
        .lineSequence()
        .mapNotNull(::normalizeWifiSsid)
        .toCollection(linkedSetOf())

internal fun encodeWifiSsids(ssids: Collection<String>): String =
    ssids.mapNotNull(::normalizeWifiSsid)
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .joinToString("\n")

internal fun validWifiSsid(raw: String?): String? = normalizeWifiSsid(raw)?.takeIf {
    it.none { char -> char == '\n' || char == '\r' || char == '\u0000' } &&
        it.toByteArray(Charsets.UTF_8).size <= 32
}

internal data class SavedWifiNetworks(
    val saved: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
) {
    companion object {
        fun fromClassic(raw: String?): SavedWifiNetworks = runCatching {
            val json = JSONObject(raw.orEmpty())
            fun names(key: String): Set<String> {
                val values = json.optJSONArray(key) ?: return emptySet()
                return (0 until values.length()).mapNotNull { validWifiSsid(values.opt(it) as? String) }.toSet()
            }
            val saved = names("saved")
            SavedWifiNetworks(saved, names("selected").intersect(saved))
        }.getOrDefault(SavedWifiNetworks())
    }
}

internal fun shouldPauseForWifiNetwork(
    enabled: Boolean,
    wifiConnected: Boolean,
    currentSsid: String?,
    mode: WifiNetworkListMode,
    configuredSsids: Set<String>,
): Boolean {
    if (!enabled || !wifiConnected) return false

    val ssid = normalizeWifiSsid(currentSsid)
    val normalizedConfigured = configuredSsids.mapNotNull(::normalizeWifiSsid).toSet()
    val listed = ssid != null && normalizedConfigured.any { it.equals(ssid, ignoreCase = false) }

    return when (mode) {
        WifiNetworkListMode.ALL -> true
        WifiNetworkListMode.WHITELIST -> !listed
        WifiNetworkListMode.BLACKLIST -> listed
    }
}
