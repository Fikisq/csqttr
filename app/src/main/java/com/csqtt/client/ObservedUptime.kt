package com.csqtt.client

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal suspend fun publishObservedUptime(
    state: MutableStateFlow<Long?>,
    seconds: () -> Long?,
) {
    state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { observed ->
        if (observed) while (true) {
            state.value = seconds()
            delay(1_000L)
        }
    }
}
