package com.csqtt.client

import com.csqtt.client.ui.Native250WifiNetworks

/** Copies only the four added controls; credentials and existing profiles stay in their original store. */
internal fun native250LegacySettings(legacy: Map<String, Any?>): Map<String, String> = buildMap {
    for (profile in 0..31) {
        val suffix = if (profile == 0) "" else "_$profile"
        if (profile > 3 && legacy.keys.none { it.endsWith(suffix) }) continue
        val strategy = GoobyPolicy.normalizeStrategy(legacy["gooby_strategy$suffix"] as? String)
        put("gooby.$profile", if (legacy["gooby_enabled$suffix"] == true) strategy else "off")
        put("extraWorkers.$profile", (legacy["extra_workers$suffix"] == true).toString())
    }
    put("battery.mode", (legacy["battery_saver_mode"] as? String)?.takeIf { value ->
        BatterySaver.MODES.any { it.first == value }
    } ?: BatterySaver.DEFAULT_MODE)
    put("battery.percent", ((legacy["battery_saver_percent"] as? Number)?.toInt() ?: 30).coerceIn(1, 100).toString())
    val selected = decodeWifiSsids(legacy["wifi_network_list_ssids"] as? String)
    val saved = decodeWifiSsids(legacy["wifi_saved_ssids"] as? String) + selected
    put("wifi.enabled", (legacy["auto_pause_on_wifi"] == true).toString())
    val mode = legacy["wifi_network_list_mode"] as? String
    put("wifi.rule", if (mode == null && selected.isNotEmpty()) "whitelist" else WifiNetworkListMode.fromStored(mode).storedValue)
    put("wifi.networks", Native250WifiNetworks(saved, selected).encode())
}
