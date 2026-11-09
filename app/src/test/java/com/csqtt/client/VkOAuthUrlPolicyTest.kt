package com.csqtt.client

import org.junit.Assert.*
import org.junit.Test

class VkOAuthUrlPolicyTest {
    @Test fun configuredCallbackAndRelativeCallbackRemainAccepted() {
        assertTrue(VkOAuthUrlPolicy.isCallback(CsqttConstants.VkAutoHash.VK_OAUTH_REDIRECT_URI))
        assertEquals("https://oauth.vk.ru/blank.html#access_token=test",
            VkOAuthUrlPolicy.resolve("https://oauth.vk.ru/authorize", "blank.html#access_token=test"))
        assertTrue(VkOAuthUrlPolicy.isCallback("https://oauth.vk.ru:443/blank.html?access_token=test"))
    }

    @Test fun officialFlowsAndRelativeRedirectsWork() {
        assertEquals("https://login.vk.ru/?act=grant_access&a=1",
            VkOAuthUrlPolicy.resolve("https://login.vk.ru/login", "/?act=grant_access&amp;a=1"))
        assertTrue(VkOAuthUrlPolicy.isCallback("https://oauth.vk.ru/blank.html#access_token=test"))
        assertTrue(VkOAuthUrlPolicy.isCallback("https://oauth.vk.com/blank.html#access_token=test"))
        assertTrue(VkOAuthUrlPolicy.isTrusted("https://id.vk.ru/auth"))
    }

    @Test fun cookiesCannotFollowForeignOrDowngradedRedirects() {
        for (target in listOf("https://evil.example/", "//evil.example/", "http://login.vk.ru/",
            "https://login.vk.ru.evil.example/", "https://login.vk.ru@evil.example/",
            "https://user@login.vk.ru/", "https://login.vk.ru:444/", "file:///tmp/token")) {
            assertFalse(target, VkOAuthUrlPolicy.isTrusted(target))
            assertTrue(target, runCatching { VkOAuthUrlPolicy.resolve("https://oauth.vk.ru/", target) }.isFailure)
        }
    }

    @Test fun onlyExactOAuthCallbackCanSupplyToken() {
        for (url in listOf("https://evil.example/#access_token=test", "https://vk.com/#access_token=test",
            "https://oauth.vk.ru/not-blank.html#access_token=test", "http://oauth.vk.ru/blank.html",
            "https://oauth.vk.ru/%62lank.html#access_token=test",
            "https://oauth.vk.ru/blank.html/extra#access_token=test",
            "https://oauth.vk.ru.evil.example/blank.html#access_token=test")) {
            assertFalse(url, VkOAuthUrlPolicy.isCallback(url))
        }
    }
}
