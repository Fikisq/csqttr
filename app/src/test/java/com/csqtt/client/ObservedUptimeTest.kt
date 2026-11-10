package com.csqtt.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObservedUptimeTest {
    @Test fun idleWithoutObserversAndCatchesUpOnReturn() = runTest {
        val state = MutableStateFlow<Long?>(0L)
        var reads = 0
        val ticker = backgroundScope.launch {
            publishObservedUptime(state) { reads++; testScheduler.currentTime / 1000 }
        }
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(0, reads)
        val observer = backgroundScope.launch { state.collect {} }
        runCurrent()
        assertEquals(5L, state.value)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(7L, state.value)
        observer.cancel()
        runCurrent()
        val stoppedReads = reads
        advanceTimeBy(10000)
        runCurrent()
        assertEquals(stoppedReads, reads)
        val returned = backgroundScope.launch { state.collect {} }
        runCurrent()
        assertEquals(17L, state.value)
        returned.cancel()
        ticker.cancel()
    }
}
