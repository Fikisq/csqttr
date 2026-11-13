package com.csqtt.client

enum class GoobyMode(val storedValue: String, val label: String) {
    OFF("off", "Выкл"),
    GENTLE("gentle", "Лёгкая"),
    CURRENT("current", "Средняя"),
    EXTENDED("extended", "Тяжёлая");

    fun arguments(transport: String): List<String> =
        if (this != OFF && isAvailable(transport)) listOf("--gooby", "--gooby-strategy", storedValue)
        else emptyList()

    companion object {
        const val EXTRA = "gooby_mode"

        fun isAvailable(transport: String): Boolean =
            transport == CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS

        fun fromStored(value: String?): GoobyMode =
            entries.firstOrNull { it.storedValue == value } ?: OFF

        fun fromLegacy(enabled: Boolean, strategy: String?): GoobyMode =
            if (enabled) fromStored(strategy).takeUnless { it == OFF } ?: CURRENT else OFF
    }
}
