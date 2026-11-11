package com.csqtt.client

import org.junit.Assert.*
import org.junit.Test

class GoobyLogParserTest {
    private val applied = "[GOOBY] TURN/TCP: разделение Allocate применено (без изменения TTL)"

    @Test fun nativeApplicationMessagesArePreservedForTheOriginalTerminal() {
        val lines = listOf(
            applied,
            "[GOOBY] TLS: разделение ClientHello применено",
            "[GOOBY] Начальные данные не подходят для стратегии; переданы без обработки",
            "[GOOBY] Обработано Allocate: 200 | ClientHello: 0 | Без обработки: 0",
        )
        for (line in lines) {
            val entry = requireNotNull(GoobyLogParser.parse(line))
            assertEquals(line, entry.message)
            assertTrue(entry.key.startsWith("gooby_"))
        }
    }

    @Test fun startupAndPassthroughAreNotReportedAsAppliedStrategy() {
        val started = requireNotNull(GoobyLogParser.parse("[КЛИЕНТ] Встроенный Gooby запущен"))
        val split = requireNotNull(GoobyLogParser.parse(applied))
        val tls = requireNotNull(GoobyLogParser.parse("[GOOBY] TLS: разделение ClientHello применено"))
        val passthrough = requireNotNull(GoobyLogParser.parse("[GOOBY] Начальные данные не подходят для стратегии; переданы без обработки"))
        assertEquals(LogLevel.LOG, started.level)
        assertEquals(LogLevel.OK, split.level)
        assertEquals(LogLevel.OK, tls.level)
        assertEquals(LogLevel.LOG, passthrough.level)
        assertEquals(4, setOf(started.key, split.key, tls.key, passthrough.key).size)
        assertFalse(started.isError)
    }

    @Test fun totalsWithTurnErrorNumbersStayVisibleAndDoNotOverwriteAppliedEvent() {
        val line = "[GOOBY] Обработано Allocate: 486 | ClientHello: 437 | Без обработки: 0"
        // Counters are not STUN error codes, even in the generic recovery classifier.
        assertFalse(TunnelLogPolicy.isInternalRecovery(line))
        val entry = requireNotNull(GoobyLogParser.parse(line))
        assertFalse(entry.isError)
        assertEquals(line, entry.message)
        assertNotEquals(requireNotNull(GoobyLogParser.parse(applied)).key, entry.key)
        assertEquals(entry.key, requireNotNull(GoobyLogParser.parse("[GOOBY] Обработано Allocate: 200 | ClientHello: 0 | Без обработки: 0")).key)
    }

    @Test fun goobyErrorsSurviveDisabledLoggingButSuccessHonorsTheSetting() {
        val error = requireNotNull(GoobyLogParser.parse("[GOOBY] TURN allocation timeout"))
        assertTrue(error.isError)
        assertTrue(TunnelLogPolicy.isAllowedWhenInactiveLogging(error.key, error.isError, error.level))
        assertEquals(LogLevel.ERROR, error.level)
        val success = requireNotNull(GoobyLogParser.parse(applied))
        assertFalse(TunnelLogPolicy.isAllowedWhenInactiveLogging(success.key, success.isError, success.level))
    }

    @Test fun unrelatedAndEmptyLinesAreNotIntercepted() {
        for (line in listOf("", "[GOOBY]  ", "[TURN] ChannelBind активен", "[КЛИЕНТ] Gooby: 127.0.0.1:1234; SOCKS5 CONNECT", "text [GOOBY] payload")) {
            assertNull(GoobyLogParser.parse(line))
        }
    }

    @Test fun futureMessagesAreVisibleWithBoundedKeysWithoutClaimingSuccess() {
        val first = requireNotNull(GoobyLogParser.parse("[GOOBY] Status from newer core: 1"))
        val second = requireNotNull(GoobyLogParser.parse("[GOOBY] Status from newer core: 2"))
        assertEquals(first.key, second.key)
        assertEquals(LogLevel.LOG, first.level)
        assertEquals("[GOOBY] Status from newer core: 1", first.message)
    }
}
