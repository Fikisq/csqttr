package com.csqtt.client

import java.net.URI

/** Validate before attaching cookies or accepting an OAuth result. */
internal object VkOAuthUrlPolicy {
    private val hosts = setOf(
        "vk.com", "vk.ru", "m.vk.com", "m.vk.ru", "id.vk.com", "id.vk.ru",
        "login.vk.com", "login.vk.ru", "oauth.vk.com", "oauth.vk.ru",
    )

    fun isTrusted(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", true) && uri.host?.lowercase() in hosts &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    fun resolve(base: String, target: String): String {
        val resolved = URI(base).resolve(target.replace("&amp;", "&")).toString()
        require(isTrusted(resolved)) { "Недоверенный адрес перенаправления VK" }
        return resolved
    }

    fun isCallback(url: String): Boolean = isTrusted(url) && runCatching {
        val uri = URI(url)
        uri.host.lowercase() in setOf("oauth.vk.com", "oauth.vk.ru") && uri.rawPath == "/blank.html"
    }.getOrDefault(false)
}
