package com.csqtt.client

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class DeployedConnectionTest {
    @Test fun removedConnectionIsNotModifiedByDelayedDeployResult() {
        for (profile in listOf(0, 3)) {
            fun key(name: String) = if (profile == 0) name else "${name}_$profile"
            val prefs = mutablePreferencesOf(
                booleanPreferencesKey(key("profile_removed")) to true,
                stringPreferencesKey(key("peer")) to "old.example",
                stringPreferencesKey(key("connection_password_local")) to "old-test-password",
                stringPreferencesKey(key("connection_password_encrypted")) to "old-ciphertext",
                intPreferencesKey(key("server_peer_port")) to 46000,
                longPreferencesKey(key("profile_revision")) to 8L,
                intPreferencesKey("active_profile") to 6,
            )
            val before = prefs.toPreferences()
            assertFalse(SettingsStore.updateDeployedConnection(prefs, profile, "new.example", 56000, "new-test-password"))
            assertEquals(before, prefs.toPreferences())
        }
    }

    @Test fun existingConnectionReceivesResultWithoutChangingSelectionOrOtherProfiles() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("active_profile") to 6,
            stringPreferencesKey("peer_6") to "other.example",
            stringPreferencesKey("connection_password_encrypted_3") to "old-ciphertext",
            longPreferencesKey("profile_revision_3") to 8L,
        )
        assertTrue(SettingsStore.updateDeployedConnection(prefs, 3, "new.example", 56000, "new-test-password"))
        assertEquals("new.example", prefs[stringPreferencesKey("peer_3")])
        assertEquals("new-test-password", prefs[stringPreferencesKey("connection_password_local_3")])
        assertNull(prefs[stringPreferencesKey("connection_password_encrypted_3")])
        assertEquals(56000, prefs[intPreferencesKey("server_peer_port_3")])
        assertEquals(9L, prefs[longPreferencesKey("profile_revision_3")])
        assertEquals(6, prefs[intPreferencesKey("active_profile")])
        assertEquals("other.example", prefs[stringPreferencesKey("peer_6")])
    }

    @Test fun absentPasswordDoesNotEraseExistingPassword() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("connection_password_local") to "keep-test-password")
        assertTrue(SettingsStore.updateDeployedConnection(prefs, 0, "", 46000, null))
        assertEquals("keep-test-password", prefs[stringPreferencesKey("connection_password_local")])
        assertNull(prefs[stringPreferencesKey("peer")])
    }
}
