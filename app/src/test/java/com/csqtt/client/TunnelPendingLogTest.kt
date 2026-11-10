package com.csqtt.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TunnelPendingLogTest {
    @Test fun backgroundBatchPreservesCountsAndForegroundFlushesImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            TunnelManager.clearLogs()
            runCurrent()
            TunnelManager.setUiForeground(false)
            repeat(12) { TunnelManager.updateLog("background_test", "Message", 99) }
            runCurrent()
            advanceTimeBy(121)
            runCurrent()
            assertFalse(TunnelManager.logs.any { it.key == "background_test" })
            TunnelManager.setUiForeground(true)
            runCurrent()
            assertEquals(12, TunnelManager.logs.single { it.key == "background_test" }.count)
            advanceTimeBy(1001)
            runCurrent()
            assertEquals(12, TunnelManager.logs.single { it.key == "background_test" }.count)
        } finally {
            TunnelManager.setUiForeground(false)
            TunnelManager.clearLogs()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun backgroundFlushesWithoutOpeningUiAndErrorsDoNotWait() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            TunnelManager.clearLogs()
            runCurrent()
            TunnelManager.setUiForeground(false)
            TunnelManager.updateLog("background_timer", "Normal", 99)
            runCurrent()
            advanceTimeBy(1001)
            runCurrent()
            assertEquals(1, TunnelManager.logs.single { it.key == "background_timer" }.count)
            TunnelManager.updateLog("background_error", "Error", 200, true)
            runCurrent()
            assertTrue(TunnelManager.logs.any { it.key == "background_error" && it.isError })
        } finally {
            TunnelManager.clearLogs()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
    private fun field(name: String) = TunnelManager::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test fun explicitClearRemovesPendingMessages() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            TunnelManager.clearLogs()
            runCurrent()
            TunnelManager.updateLog("clear_test", "Pending", 99)
            runCurrent()
            TunnelManager.clearLogs()
            runCurrent()
            advanceTimeBy(1001)
            runCurrent()
            assertFalse(TunnelManager.logs.any { it.key == "clear_test" })
        } finally {
            TunnelManager.clearLogs()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun acceptedTailSurvivesExitButLateOldProcessMessagesAreRejected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val lifecycle = field("lifecycleState").get(null) as TunnelLifecycleState
        val identityClass = TunnelManager::class.java.declaredClasses.single { it.simpleName == "ProcessIdentity" }
        val constructor = identityClass.declaredConstructors.single { it.parameterCount == 5 }.apply { isAccessible = true }
        val process = mock(Process::class.java)
        val ticket = requireNotNull(lifecycle.reserveProcess(lifecycle.requestStart(), true))
        val identity = constructor.newInstance(process, 101L, ticket, 1000L, TunnelStartupProgress())
        val enqueue = TunnelManager::class.java.getDeclaredMethod("updateLogForProcess", identityClass,
            String::class.java, String::class.java, Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, LogLevel::class.java).apply { isAccessible = true }
        try {
            TunnelManager.clearLogs()
            runCurrent()
            field("process").set(null, process)
            field("processGeneration").setLong(null, 101L)
            field("processIdentity").set(null, identity)
            enqueue.invoke(TunnelManager, identity, "tail_test", "Accepted before exit", 99, false, LogLevel.LOG)
            lifecycle.requestStop()
            field("processIdentity").set(null, null)
            enqueue.invoke(TunnelManager, identity, "stale_test", "Late stale callback", 99, false, LogLevel.LOG)
            val nextTicket = requireNotNull(lifecycle.reserveProcess(lifecycle.requestStart(), true))
            val nextIdentity = constructor.newInstance(process, 102L, nextTicket, 1001L, TunnelStartupProgress())
            field("processGeneration").setLong(null, 102L)
            field("processIdentity").set(null, nextIdentity)
            repeat(2) {
                enqueue.invoke(TunnelManager, nextIdentity, "tail_test", "New connection", 99, false, LogLevel.LOG)
            }
            TunnelManager.updateLog("deploy_test", "Deploy is still running", 99)
            advanceTimeBy(3001)
            runCurrent()
            assertTrue(TunnelManager.logs.any { it.key == "tail_test" })
            assertTrue(TunnelManager.logs.any { it.key == "deploy_test" })
            assertFalse(TunnelManager.logs.any { it.key == "stale_test" })
            val sessions = TunnelManager.logs.filter { it.key == "tail_test" }
            assertEquals(listOf(101L, 102L), sessions.map { it.sessionId })
            assertEquals(listOf(1, 2), sessions.map { it.count })
            assertEquals(2, sessions.map { it.listKey }.distinct().size)
            assertTrue(sessions.last().exportMessage.contains("#102"))
            // A later flush in the same session still aggregates only that session.
            enqueue.invoke(TunnelManager, nextIdentity, "tail_test", "New connection again", 99, false, LogLevel.LOG)
            advanceTimeBy(1001)
            runCurrent()
            assertEquals(listOf(1, 3), TunnelManager.logs.filter { it.key == "tail_test" }.map { it.count })
        } finally {
            lifecycle.requestStop()
            field("processIdentity").set(null, null)
            field("process").set(null, null)
            TunnelManager.clearLogs()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
