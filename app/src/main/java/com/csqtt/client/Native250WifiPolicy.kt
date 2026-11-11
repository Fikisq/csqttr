package com.csqtt.client

import com.csqtt.client.ui.Native250WifiNetworks

internal fun native250ShouldPauseForWifi(config: Native250RuntimeConfig, connected: Boolean, ssid: String?): Boolean =
    shouldPauseForWifiNetwork(
        enabled = config.flag("wifi.enabled"),
        wifiConnected = connected,
        currentSsid = ssid,
        mode = WifiNetworkListMode.fromStored(config.value("wifi.rule", "all")),
        configuredSsids = Native250WifiNetworks.decode(config.value("wifi.networks", "{}")).selected,
    )
