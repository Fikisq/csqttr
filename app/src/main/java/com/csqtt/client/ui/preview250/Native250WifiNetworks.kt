package com.csqtt.client.ui

import org.json.JSONArray
import org.json.JSONObject

internal fun native250Ssid(raw: String): String? {
    val value = raw.trim().removeSurrounding("\"").trim()
    return value.takeIf {
        it.isNotEmpty() && !it.equals("<unknown ssid>", ignoreCase = true) &&
            it.none { c -> c == '\n' || c == '\r' || c == '\u0000' } && it.toByteArray(Charsets.UTF_8).size <= 32
    }
}

/** Save both lists together so deleting a network never leaves an orphan selection. */
internal data class Native250WifiNetworks(val saved: Set<String> = emptySet(), val selected: Set<String> = emptySet()) {
    fun remember(name: String, rule: String) = copy(
        saved = saved + name,
        selected = if (rule == "all") selected else selected + name,
    )
    fun forget(name: String) = copy(saved = saved - name, selected = selected - name)
    fun select(name: String, checked: Boolean) =
        if (name !in saved) this else copy(selected = if (checked) selected + name else selected - name)

    fun encode(): String = JSONObject()
        .put("saved", JSONArray(saved.sorted()))
        .put("selected", JSONArray(selected.intersect(saved).sorted())).toString()

    companion object {
        fun decode(raw: String): Native250WifiNetworks = runCatching {
            val json = JSONObject(raw)
            fun names(key: String): Set<String> {
                val array = json.optJSONArray(key) ?: return emptySet()
                return (0 until array.length()).mapNotNull { index ->
                    (array.opt(index) as? String)?.let(::native250Ssid)
                }.toSet()
            }
            val saved = names("saved")
            Native250WifiNetworks(saved, names("selected").intersect(saved))
        }.getOrDefault(Native250WifiNetworks())
    }
}
