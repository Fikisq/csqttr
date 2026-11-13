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
        val deployStore = SettingsStore(context, deployScope = true)
        val saved = deployStore.deploySettingsSnapshot.first { it.isLoaded }
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
                        "passwords", "docker-fixture-add", "docker-fixture-remove", "systemd-fixture-remove" -> {
                            if (check == "passwords") {
                                val slot = deployStore.linkedConnectionProfile(saved.profile)
                                val auth = SettingsStore(context, readOnlyProfile = slot)
                                    .tunnelAuthSnapshot.first { it.profile >= 0 }
                                assertEquals(slot, auth.profile)
                                assertTrue("Deploy and Tunnel passwords match", saved.mainPassword == auth.connectionPassword)
                                val generated = Regex("[A-Za-z0-9]{16}")
                                assertTrue("Both generated passwords match the expected format",
                                    generated.matches(saved.mainPassword) && generated.matches(saved.webPassword))
                                assertTrue("The generated passwords are different", saved.mainPassword != saved.webPassword)
                            }
                            val path = saved.secretPathUuid.trim().trim('/')
                            require(path.matches(Regex("[A-Za-z0-9_-]*")))
                            val endpoint = "https://${saved.ip}:46002" + (if (path.isEmpty()) "" else "/$path")
                            val payload = org.json.JSONObject().put("user", saved.webLogin).put("pass", saved.webPassword)
                            if (check == "passwords") """
                                set -eu
                                printf '%s' '${payload.toString().replace("'", "'\"'\"'")}' |
                                    curl --silent --show-error --fail --max-time 10 \
                                    --cacert /etc/csqtt/web_cert.pem \
                                    --connect-to '${saved.ip}:46002:127.0.0.1:46002' \
                                    -H 'Content-Type: application/json' --data-binary @- '$endpoint/api/login' -o /dev/null
                                printf 'PASSWORDS_OK\n'
                            """.trimIndent() else """
                                set -eu
                                if [ "$check" = systemd-fixture-remove ]; then
                                    systemctl is-active --quiet csqtt.service
                                else
                                    test "${'$'}(docker inspect --format '{{.State.Running}}' csqtt)" = true
                                fi
                                python3 - <<'PY'
                                import json
                                import subprocess
                                import urllib.parse

                                endpoint = '$endpoint'
                                curl = ["curl", "-fsS", "--max-time", "20", "--cacert", "/etc/csqtt/web_cert.pem",
                                        "--connect-to", "${saved.ip}:46002:127.0.0.1:46002"]
                                login = subprocess.run(curl + ["-D", "-", "-o", "/dev/null",
                                    "-H", "Content-Type: application/json", "--data-binary", "@-", endpoint + "/api/login"],
                                    input=${org.json.JSONObject.quote(payload.toString())},
                                    text=True, capture_output=True, check=True)
                                cookie = next(line.split(":", 1)[1].strip().split(";", 1)[0]
                                              for line in login.stdout.splitlines() if line.lower().startswith("set-cookie:"))

                                def api(path, method="GET", data=None):
                                    command = curl + ["-H", "Cookie: " + cookie, "-X", method, endpoint + "/api/" + path]
                                    if data is not None:
                                        command += ["-H", "Content-Type: application/json", "--data-binary", "@-"]
                                    response = subprocess.run(command, input=None if data is None else json.dumps(data),
                                                              text=True, capture_output=True)
                                    if response.returncode != 0:
                                        raise RuntimeError("CSQTTR API request failed: " + method + " " + path.split("/")[0])
                                    return response.stdout

                                name = "QA 60.14 Docker retention"
                                token = "qa-csqttr-6014-inert-token"
                                client_hashes = "qa6014-inert-hash-one,qa6014-inert-hash-two"

                                def cleanup(state):
                                    for rule_id, rule in state["routing_rules"].items():
                                        if rule["name"] == name:
                                            api("routing-rules/" + urllib.parse.quote(rule_id, safe=""), "DELETE")
                                    for client in state["clients"]:
                                        if client["name"] == name and client["vk_hashes"] == client_hashes:
                                            api("clients/" + urllib.parse.quote(client["password"], safe=""), "DELETE")
                                    if state["vk_access_token"] == token:
                                        api("settings", "POST", {"vk_token": ""})

                                state = json.loads(api("export"))
                                try:
                                    if "$check" == "docker-fixture-add":
                                        assert not state["clients"] and not state["routing_rules"] and not state["vk_access_token"]
                                        try:
                                            client = json.loads(api("clients", "POST",
                                                {"name": name, "days": 1, "hash": client_hashes}))
                                            api("clients/" + urllib.parse.quote(client["password"], safe="") + "/toggle", "POST")
                                            api("routing-rules", "POST", {"name": name, "packages": ["org.example.qa"],
                                                "domains": ["fixture.example"], "ips": ["192.0.2.0/24"],
                                                "assigned_clients": [client["password"]]})
                                            api("settings", "POST", {"vk_token": token})
                                        except Exception:
                                            cleanup(json.loads(api("export")))
                                            raise
                                        print("FIXTURE_ADDED")
                                    else:
                                        cleanup(state)
                                        print("FIXTURE_REMOVED")
                                finally:
                                    api("logout", "POST")
                                PY
                            """.trimIndent()
                        }
                        "docker-state", "systemd-state" -> """
                            set -eu
                            if [ "$check" = systemd-state ]; then
                                systemctl is-active --quiet csqtt.service
                                test -z "${'$'}(docker ps -q --filter 'name=^/csqtt${'$'}')"
                                test -n "${'$'}(ss -H -lun 'sport = :46000')"
                                test -n "${'$'}(ss -H -ltn 'sport = :46002')"
                            else
                                test "${'$'}(docker inspect --format '{{.State.Running}}' csqtt)" = true
                                test "${'$'}(docker inspect --format '{{index .Config.Labels "com.csqtt.managed"}}' csqtt)" = true
                            fi
                            python3 - <<'PY'
                            import hashlib
                            import json
                            import sqlite3

                            def fingerprint(label, value, populated):
                                encoded = json.dumps(value, separators=(",", ":"), ensure_ascii=True).encode()
                                print(f"STATE {label} {populated} {hashlib.sha256(encoded).hexdigest()}")

                            db = sqlite3.connect("file:/etc/csqtt/csqtt.db?mode=ro", uri=True)
                            db.execute("BEGIN")
                            clients = db.execute(
                                "SELECT password, name, expires_at, is_deactivated, ports, vk_token_enabled, "
                                "dtls_port, wg_port, local_port, group_id, routing_rule_id, sub_uuid, workers "
                                "FROM passwords ORDER BY password"
                            ).fetchall()
                            hashes = db.execute(
                                "SELECT password, vk_hash, vk_hashes FROM passwords ORDER BY password"
                            ).fetchall()
                            groups = db.execute("SELECT * FROM client_groups ORDER BY id").fetchall()
                            rules = db.execute("SELECT * FROM routing_rules ORDER BY id").fetchall()
                            meta = dict(db.execute(
                                "SELECT key, value FROM meta WHERE key IN ('vk_access_token', 'dns', 'dns_provider')"
                            ))
                            fingerprint("clients", clients, len(clients))
                            fingerprint("hashes", hashes, sum(bool(row[1] or row[2]) for row in hashes))
                            fingerprint("groups", groups, len(groups))
                            fingerprint("token", meta.get("vk_access_token"), int(bool(meta.get("vk_access_token"))))
                            fingerprint("rules", rules, len(rules))
                            fingerprint("dns", [meta.get("dns"), meta.get("dns_provider")], int(bool(meta.get("dns"))))
                            PY
                        """.trimIndent()
                        "certbot-state" -> """
                            set -eu
                            if certbot=${'$'}(command -v certbot); then
                                printf 'CERTBOT_SYSTEM %s ' "${'$'}certbot"
                                timeout 5 "${'$'}certbot" --version 2>&1
                                sha256sum "${'$'}certbot" | awk '{print "CERTBOT_SYSTEM_SHA256 " ${'$'}1}'
                                help=${'$'}(timeout 5 "${'$'}certbot" certonly --help all 2>&1 || true)
                                if printf '%s\n' "${'$'}help" | grep -q -- --ip-address &&
                                   printf '%s\n' "${'$'}help" | grep -q -- --no-directory-hooks; then
                                    echo CERTBOT_SYSTEM_IP_SUPPORTED
                                else
                                    echo CERTBOT_SYSTEM_IP_UNSUPPORTED
                                fi
                            else
                                echo CERTBOT_SYSTEM_MISSING
                            fi
                            if [ -x /opt/csqtt-certbot/bin/certbot ]; then
                                printf 'CERTBOT_CSQTT '
                                timeout 5 /opt/csqtt-certbot/bin/certbot --version 2>&1
                            fi
                            if [ -n "${'$'}(ss -H -ltn 'sport = :80')" ]; then
                                echo CERTBOT_PORT_80_BUSY
                            fi
                            tac /var/log/csqtt-install.log | sed '/Повторная установка: данные клиентов и правила сохраняются/q' |
                                tac | grep -E 'Используется (установленный системный|ранее установленный) Certbot|Системный Certbot не подходит|TCP/80 уже занят|IP-сертификат (действителен|активирован)|Let.s Encrypt не подтвердил IP|Подготовка Let.s Encrypt клиента|самоподписанный сертификат|Общее время deploy.sh' |
                                sed 's/^/CERTBOT_LOG /' || true
                        """.trimIndent()
                        else -> error("Unknown CSQTT state check")
                    }
                    ssh.startSession().use { session ->
                        session.exec(rootCommand(command, saved.sshPassword.takeUnless { saved.sshKeysMode })).use { cmd ->
                            cmd.join(30, TimeUnit.SECONDS)
                            assertEquals("Scoped CSQTT state check", 0, cmd.exitStatus)
                            val result = cmd.inputStream.bufferedReader().readText().trim()
                            if (check == "docker-state" || check == "systemd-state") {
                                val lines = result.lineSequence().toList()
                                val record = Regex("STATE (clients|hashes|groups|token|rules|dns) [0-9]+ [0-9a-f]{64}")
                                assertTrue("Expected six redacted fingerprints",
                                    lines.size == 6 && lines.all(record::matches) &&
                                        lines.map { it.split(' ')[1] }.distinct().size == 6)
                            } else if (check == "certbot-state") {
                                assertTrue("Expected scoped Certbot and installer evidence",
                                    result.isNotBlank() && result.lineSequence().all { it.startsWith("CERTBOT_") })
                            } else {
                                assertTrue(result in setOf("REMOVED_OK", "RESTORED_OK", "PASSWORDS_OK",
                                    "FIXTURE_ADDED", "FIXTURE_REMOVED") ||
                                    result.matches(Regex("BACKUP_OK /root/csqtt-before-60\\.14-[A-Za-z0-9]{8}\\.tar")))
                            }
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
