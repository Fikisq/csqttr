package com.csqtt.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TunnelPendingLogTest {
    private fun field(name: String) = TunnelManager::class.java.getDeclaredField(name).apply { isAccessible = true }

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
            TunnelManager.updateLog("deploy_test", "Deploy is still running", 99)
            advanceTimeBy(3001)
            runCurrent()
            assertTrue(TunnelManager.logs.any { it.key == "tail_test" })
            assertTrue(TunnelManager.logs.any { it.key == "deploy_test" })
            assertFalse(TunnelManager.logs.any { it.key == "stale_test" })
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
