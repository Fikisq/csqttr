package com.csqtt.client

import org.junit.Assert.assertEquals
import org.junit.Test

class Native250VkAccountTest {
    @Test fun existingSharedAccountWorksWithoutOverwritingProfileImports() {
        assertEquals("shared" to "user", native250VkAccount("", "", "shared", "user", false))
        assertEquals("imported" to "", native250VkAccount("imported", "", "shared", "user", false))
    }
    @Test fun explicitLogoutDoesNotSilentlySignInAgain() {
        assertEquals("" to "", native250VkAccount("", "", "shared", "user", true))
        assertEquals("new" to "other", native250VkAccount("new", "other", "shared", "user", true))
        assertEquals("" to "", native250VkAccount("", "", "", "user", false))
    }
}
