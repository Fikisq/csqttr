package com.csqtt.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.csqtt.client.ui.legacy.createSSHClient
import com.csqtt.client.ui.legacy.rootCommand
import com.csqtt.client.ui.legacy.writeDeployScript
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.io.File
import net.schmizz.sshj.xfer.FileSystemFile

/** Opt-in checks against the active profile; credentials stay on the device. */
@RunWith(AndroidJUnit4::class)
class SshConnectionSmokeTest {
    @Test
    fun configuredProfileAuthenticatesAndExecutesReadOnlyCommand() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Pass -e sshSmokeTest true to use the configured VPS", args.getString("sshSmokeTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = SettingsStore(context).deploySettingsSnapshot.first { it.isLoaded }
        assertEquals(args.getString("expectedSshHost"), saved.ip)
        assertEquals(args.getString("expectedSshPort"), saved.sshPort)
        repeat(3) {
            createSSHClient(
                context, saved.ip, saved.sshLogin, saved.sshPassword, saved.sshPort.toInt(),
                if (saved.sshKeysMode) saved.sshPrivateKey else "",
                if (saved.sshKeysMode) saved.sshKeyPassphrase else "",
                if (saved.sshKeysMode) saved.sshCertificate else "",
            ).use { ssh ->
                assertTrue(ssh.isAuthenticated)
                ssh.startSession().use { session ->
                    session.exec("printf 'CSQTT_SSH_OK\\n'; uname -m").use { cmd ->
                        cmd.join(10, TimeUnit.SECONDS)
                        assertEquals(0, cmd.exitStatus)
                        assertTrue(cmd.inputStream.bufferedReader().readText().contains("CSQTT_SSH_OK"))
                    }
                }
                if (it == 0 && args.containsKey("serverStateCheck")) {
                    assertEquals(46000, saved.serverPeerPort)
                    assertEquals(46002, saved.serverWebPort)
                    val check = args.getString("serverStateCheck")
                    val command = when (check) {
                        "backup" -> """
                            set -eu
                            umask 077
                            backup=${'$'}(mktemp /root/csqtt-before-60.14-XXXXXXXX.tar)
                            trap 'rm -f -- "${'$'}backup"' EXIT
                            tar -C /etc -cf "${'$'}backup" csqtt
                            tar -tf "${'$'}backup" | grep -Fxq csqtt/csqtt.env
                            tar -tf "${'$'}backup" | grep -Fxq csqtt/csqtt.db
                            trap - EXIT
                            printf 'BACKUP_OK %s\n' "${'$'}backup"
                        """.trimIndent()
                        "removed" -> """
                            set -eu
                            test ! -e /etc/csqtt
                            test ! -e /usr/local/bin/csqtt
                            test -z "${'$'}(systemctl is-active csqtt 2>/dev/null | grep '^active${'$'}' || true)"
                            if command -v docker >/dev/null; then
                                test -z "${'$'}(docker ps -a -q --filter 'name=^/csqtt${'$'}')"
                            fi
                            test -z "${'$'}(ss -H -lun 'sport = :46000')"
                            test -z "${'$'}(ss -H -ltn 'sport = :46002')"
                            printf 'REMOVED_OK\n'
                        """.trimIndent()
                        "restore" -> {
                            val backup = args.getString("csqttBackup").orEmpty()
                            require(backup.matches(Regex("/root/csqtt-before-60\\.14-[A-Za-z0-9]{8}\\.tar")))
                            """
                                set -eu
                                test ! -e /etc/csqtt
                                test -f '$backup'
                                tar -C /etc -xf '$backup'
                                test -s /etc/csqtt/csqtt.db
                                printf 'RESTORED_OK\n'
                            """.trimIndent()
                        }
                        else -> error("Unknown CSQTT state check")
                    }
                    ssh.startSession().use { session ->
                        session.exec(rootCommand(command, saved.sshPassword.takeUnless { saved.sshKeysMode })).use { cmd ->
                            cmd.join(30, TimeUnit.SECONDS)
                            assertEquals("Scoped CSQTT state check", 0, cmd.exitStatus)
                            val result = cmd.inputStream.bufferedReader().readText().trim()
                            assertTrue(result in setOf("REMOVED_OK", "RESTORED_OK") ||
                                result.matches(Regex("BACKUP_OK /root/csqtt-before-60\\.14-[A-Za-z0-9]{8}\\.tar")))
                            println(result)
                        }
                    }
                }
                if (it == 0 && args.getString("installerSyntaxCheck") == "true") {
                    val localScript = File.createTempFile("deploy-syntax-", ".sh", context.cacheDir)
                    val remoteScript = "/tmp/csqtt-syntax-${System.nanoTime()}.sh"
                    try {
                        writeDeployScript(context, localScript)
                        ssh.newSFTPClient().use { sftp ->
                            try {
                                sftp.put(FileSystemFile(localScript), remoteScript)
                                ssh.startSession().use { session ->
                                    session.exec("bash -n '$remoteScript'").use { cmd ->
                                        cmd.join(10, TimeUnit.SECONDS)
                                        assertEquals("Uploaded installer must parse without executing", 0, cmd.exitStatus)
                                    }
                                }
                            } finally {
                                sftp.rm(remoteScript)
                            }
                        }
                    } finally {
                        localScript.delete()
                    }
                }
                if (it == 0 && args.getString("sshDiagnostics") == "true") {
                    ssh.startSession().use { session ->
                        val command = """
                            echo 'SSH_DAEMON_LIMITS'
                            timeout 5 /usr/sbin/sshd -T 2>/dev/null | grep -E '^(maxstartups|persourcemaxstartups|persourcepenalties|logingracetime) '
                            echo 'RECENT_CLIENT_EVENTS'
                            client_ip="${'$'}{SSH_CONNECTION%% *}"
                            if [ -n "${'$'}client_ip" ]; then
                                timeout 5 journalctl -u ssh -u sshd --since '-30 min' --no-pager -n 300 2>/dev/null | grep -F "${'$'}client_ip" | tail -n 40
                            fi
                        """.trimIndent()
                        session.exec(command).use { cmd ->
                            cmd.join(20, TimeUnit.SECONDS)
                            if (cmd.exitStatus == null) cmd.close()
                            File(context.getExternalFilesDir(null), "ssh-diagnostics.log")
                                .writeText(cmd.inputStream.bufferedReader().readText())
                        }
                    }
                }
            }
        }
    }
}
