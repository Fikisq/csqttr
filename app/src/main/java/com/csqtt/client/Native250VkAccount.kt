package com.csqtt.client

internal fun native250VkAccount(
    profileToken: String, profileUserId: String, sharedToken: String, sharedUserId: String, sharedDisabled: Boolean,
): Pair<String, String> = when {
    profileToken.isNotBlank() -> profileToken to profileUserId
    !sharedDisabled && sharedToken.isNotBlank() -> sharedToken to sharedUserId
    else -> "" to ""
}
