package com.csqtt.client

internal object GoobyPolicy {
    fun fromUi(config: Native250RuntimeConfig, profile: Int): Pair<Boolean, String> {
        val mode = config.value("gooby.$profile", "off")
        return (strategies.any { it.first == mode }) to normalizeStrategy(mode)
    }
    const val DEFAULT_STRATEGY = "current"
    val strategies = listOf("current" to "Текущая", "gentle" to "Мягкая", "extended" to "Расширенная")
    fun normalizeStrategy(value: String?): String = strategies.firstOrNull { it.first == value }?.first ?: DEFAULT_STRATEGY
    fun strategyLabel(value: String): String = strategies.first { it.first == normalizeStrategy(value) }.second

    fun isActive(enabled: Boolean, transport: String): Boolean =
        enabled && transport == CsqttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS

    fun arguments(enabled: Boolean, transport: String, strategy: String = DEFAULT_STRATEGY): List<String> {
        if (!isActive(enabled, transport)) return emptyList()
        val selected = normalizeStrategy(strategy)
        return if (selected == DEFAULT_STRATEGY) listOf("--gooby") else listOf("--gooby", "--gooby-strategy", selected)
    }
}
