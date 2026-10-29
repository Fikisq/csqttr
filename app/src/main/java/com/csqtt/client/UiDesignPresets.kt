package com.csqtt.client

/** Interface presets are application-wide and independent of server profiles. */
object UiDesignPresets {
    const val NEW = "v2"
    const val CLASSIC = "classic"
    const val ORIGINAL_250 = "v2_classic_original"
    const val SOFT_250 = "v2_classic_soft"

    val options = listOf(
        NEW to "Новый",
        CLASSIC to "Классический",
        ORIGINAL_250 to "Новый · 2.5.0",
        SOFT_250 to "Новый · 2.5.0 мягкий",
    )

    fun normalize(value: String?): String = value?.takeIf { key -> options.any { it.first == key } } ?: NEW
    fun label(value: String): String = options.first { it.first == normalize(value) }.second
    fun darkPalette(value: String): String = when (value) {
        ORIGINAL_250 -> "classic_original"
        SOFT_250 -> "classic_soft"
        else -> "nordic_slate"
    }
}
