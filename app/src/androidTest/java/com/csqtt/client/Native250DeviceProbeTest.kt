package com.csqtt.client

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly selected device checks. Never deploys, removes a server, or prints credentials. */
@RunWith(AndroidJUnit4::class)
class Native250DeviceProbeTest {
    @Test fun inspectSavedDeployServerReadOnly() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val expectedHost = requireNotNull(args.getString("expectedHost")) { "Select a server explicitly" }
        val selectedProfile = requireNotNull(args.getString("profile")).toInt()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(context)
        val originalProfile = store.activeProfile.first()
        try {
            store.saveActiveProfile(selectedProfile)
            val config = store.deploySettingsSnapshot.first { it.profile == selectedProfile }
            assertEquals("Unexpected deploy target", expectedHost, config.ip)
            assertTrue("Saved SSH credentials are unavailable", config.sshPassword.isNotBlank() || config.sshPrivateKey.isNotBlank())
            val connect = Class.forName("com.csqtt.client.ui.DeployOperationsKt").getDeclaredMethod(
                "createSSHClient", Context::class.java, String::class.java, String::class.java, String::class.java,
                Int::class.javaPrimitiveType, String::class.java, String::class.java,
            ).apply { isAccessible = true }
            val keys = config.sshKeysMode
            val ssh = connect.invoke(null, context, config.ip, config.sshLogin,
                if (keys) "" else config.sshPassword, config.sshPort.toInt(),
                if (keys) config.sshPrivateKey else "", if (keys) config.sshKeyPassphrase else "") as SSHClient
            ssh.use {
                it.startSession().use { session ->
                    session.exec("""
                        set -eu
                        id -u
                        uname -m
                        if [ -f /usr/local/bin/csqtt ]; then sha256sum /usr/local/bin/csqtt; fi
                        if command -v systemctl >/dev/null 2>&1; then
                          systemctl show csqtt.service -p ActiveState -p SubState -p MainPID -p FragmentPath
                        fi
                        if command -v docker >/dev/null 2>&1; then docker ps --filter name=csqtt --format '{{.Names}} {{.Image}} {{.Status}}'; fi
                        if [ -f /etc/csqtt/csqtt.db ]; then stat -c '%s bytes database' /etc/csqtt/csqtt.db; fi
                    """.trimIndent()).use { command ->
                        val output = command.inputStream.bufferedReader().readText()
                        command.join(30, TimeUnit.SECONDS)
                        val report = File(context.filesDir, "native250-checks").apply { mkdirs() }
                        File(report, "server-readonly.txt").writeText(output)
                        assertEquals("Read-only SSH inspection failed", 0, command.exitStatus)
                    }
                }
            }
        } finally {
            store.saveActiveProfile(originalProfile)
        }
    }
}
