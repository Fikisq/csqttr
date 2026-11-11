package com.csqtt.client

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in checks around a deployment initiated by the real UI. No credentials in output. */
@RunWith(AndroidJUnit4::class)
class Native250DeploymentCheckTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get() = File(context.filesDir, "native250-checks").apply { mkdirs() }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private suspend fun selected(store: SettingsStore): DeploySettingsSnapshot {
        val args = InstrumentationRegistry.getArguments()
        val profile = requireNotNull(args.getString("profile")).toInt()
        val expectedHost = requireNotNull(args.getString("expectedHost"))
        store.saveActiveProfile(profile)
        return store.deploySettingsSnapshot.first { it.profile == profile }.also {
            assertTrue("Unexpected deploy target", it.ip == expectedHost)
            assertTrue("Missing server password", it.mainPassword.isNotBlank())
        }
    }

    private fun connect(config: DeploySettingsSnapshot): SSHClient {
        val method = Class.forName("com.csqtt.client.ui.DeployOperationsKt").getDeclaredMethod(
            "createSSHClient", Context::class.java, String::class.java, String::class.java, String::class.java,
            Int::class.javaPrimitiveType, String::class.java, String::class.java,
        ).apply { isAccessible = true }
        return method.invoke(null, context, config.ip, config.sshLogin,
            if (config.sshKeysMode) "" else config.sshPassword, config.sshPort.toInt(),
            if (config.sshKeysMode) config.sshPrivateKey else "",
            if (config.sshKeysMode) config.sshKeyPassphrase else "") as SSHClient
    }

    @Test fun backupServerAndStagePasswordMismatch() = runBlocking(Dispatchers.IO) {
        require(InstrumentationRegistry.getArguments().getString("stagePasswordMismatch") == "true")
        val archive = File(directory, "server-before-deploy.tar")
        require(!archive.exists()) { "Existing backup must not be overwritten" }
        val store = SettingsStore(context)
        val originalProfile = store.activeProfile.first()
        val config = selected(store)
        connect(config).use { ssh ->
            ssh.startSession().use { session ->
                // Online SQLite backup keeps the client database consistent while the server runs.
                session.exec("""
                    set -eu
                    snapshot=${'$'}(mktemp -d /tmp/csqtt-native250-check.XXXXXX)
                    trap 'rm -rf -- "${'$'}snapshot"' EXIT
                    cp -a /etc/csqtt "${'$'}snapshot/config"
                    python3 - "${'$'}snapshot/config/csqtt.db" <<'PY'
                    import sqlite3, sys
                    source = sqlite3.connect('file:/etc/csqtt/csqtt.db?mode=ro', uri=True)
                    target = sqlite3.connect(sys.argv[1])
                    source.backup(target)
                    assert target.execute('pragma integrity_check').fetchone()[0] == 'ok'
                    target.close()
                    source.close()
                    PY
                    docker inspect csqtt > "${'$'}snapshot/docker.json"
                    tar -cf - -C "${'$'}snapshot" config docker.json -C / usr/local/bin/csqtt
                """.trimIndent()).use { command ->
                    archive.outputStream().use { command.inputStream.copyTo(it) }
                    command.join(60, TimeUnit.SECONDS)
                    assertEquals("Server backup failed", 0, command.exitStatus)
                    assertTrue("Server backup is incomplete", archive.length() > 1_000_000)
                }
            }
        }
        val linked = store.deployConnectionProfile(config.profile)
        File(directory, "deploy-expectations.json").writeText(JSONObject()
            .put("originalProfile", originalProfile).put("deployProfile", config.profile)
            .put("linkedProfile", linked).put("expectedPasswordSha256", digest(config.mainPassword))
            .toString())
        store.saveConnectionPassword(config.mainPassword + "-native250-mismatch", linked)
        // Leave the explicitly selected deploy profile open for pressing the actual UI button.
    }

    @Test fun verifyPasswordAfterUiDeployment() = runBlocking(Dispatchers.IO) {
        val expected = JSONObject(File(directory, "deploy-expectations.json").readText())
        val store = SettingsStore(context)
        val config = selected(store)
        assertEquals(expected.getInt("deployProfile"), config.profile)
        val linked = store.deployConnectionProfile(config.profile)
        assertEquals(expected.getInt("linkedProfile"), linked)
        store.saveActiveProfile(linked)
        val auth = store.tunnelAuthSnapshot.first { it.profile == linked }
        assertTrue("Deploy password was not synchronized into the linked tunnel",
            digest(auth.connectionPassword) == expected.getString("expectedPasswordSha256"))
        connect(config).use { ssh ->
            ssh.startSession().use { session ->
                session.exec("docker inspect --format '{{.State.Running}}' csqtt; sha256sum /usr/local/bin/csqtt").use { command ->
                    val output = command.inputStream.bufferedReader().readText()
                    command.join(30, TimeUnit.SECONDS)
                    assertEquals(0, command.exitStatus)
                    assertTrue("Docker server is not running", output.lineSequence().first() == "true")
                    File(directory, "server-after-deploy.txt").writeText(output)
                }
            }
        }
        store.saveActiveProfile(expected.getInt("originalProfile"))
    }
}
