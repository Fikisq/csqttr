package com.csqtt.client

import com.csqtt.client.ui.legacy.utils.parseCsqttLink
import com.csqtt.client.ui.legacy.utils.peerAddress
import kotlinx.coroutines.flow.first

data class ConnectionSource(
    val peer: String,
    val password: String,
    val hashes: String,
    val hashesFromLink: Boolean,
    val vkAccessToken: String,
    val tokenFromLink: Boolean,
)

internal enum class ManualHashGroup(val label: String) {
    Owner("От владельца"),
    Own("Свои"),
}

internal fun selectManualHashGroup(
    ownerHashes: List<String>,
    saved: ManualHashGroup?,
    previouslyRemoved: Set<String> = emptySet(),
): ManualHashGroup = when {
    ownerHashes.isEmpty() -> ManualHashGroup.Own
    saved != null -> saved
    ownerHashes.all { it in previouslyRemoved } -> ManualHashGroup.Own
    else -> ManualHashGroup.Owner
}

internal fun participantHashes(linkHashes: List<String>, group: ManualHashGroup, ownHashes: String): String =
    if (group == ManualHashGroup.Owner) linkHashes.joinToString(",") else ownHashes

suspend fun resolveConnectionSource(store: SettingsStore, updateLogMask: Boolean = true): ConnectionSource? {
    val invalidHashes = VkHashValidationCodec.decode(store.vkHashCheckResults.first())
    fun activeHashes(raw: String): String {
        val rawList = raw.split(Regex("[,\\s\\n]+")).map(String::trim).filter { it.length >= 16 }.distinct()
        val filtered = VkHashValidationCodec.active(rawList, invalidHashes)
        return (if (filtered.isNotEmpty()) filtered else rawList).joinToString(",")
    }

    if (store.csqttLinkMode.first()) {
        val link = parseCsqttLink(store.csqttLink.first()) ?: return null
        if (updateLogMask) TunnelManager.ownerHashes = link.hashes
        val tokenFromLink = link.vkAccessToken.isNotEmpty()
        val useManualHashes = !tokenFromLink &&
            store.vkHashMode.first() == CsqttConstants.VkAutoHash.MODE_MANUAL &&
            store.vkAuthMode.first() != CsqttConstants.VkAuth.MODE_AUTO_JS
        return ConnectionSource(
            peer = link.peerAddress(),
            password = link.password,
            hashes = if (useManualHashes) activeHashes(store.readParticipantHashes(link.hashes)) else "",
            hashesFromLink = useManualHashes && link.hashes.isNotEmpty(),
            vkAccessToken = link.vkAccessToken,
            tokenFromLink = tokenFromLink,
        )
    }

    if (updateLogMask) TunnelManager.ownerHashes = emptyList()
    val basePeer = store.peer.first()
    val passwordState = store.connectionPasswordState.first()
    val password = passwordState.value
    if (basePeer.isBlank()) return null
    val serverPeerPort = store.serverPeerPort.first().takeIf { it in 1..65535 }
        ?: CsqttConstants.Network.DEFAULT_SERVER_PEER_PORT
    val peer = if (basePeer.contains(':')) basePeer else "$basePeer:$serverPeerPort"
    return ConnectionSource(
        peer = peer,
        password = password,
        hashes = activeHashes(store.vkHashes.first()),
        hashesFromLink = false,
        vkAccessToken = "",
        tokenFromLink = false,
    )
}
