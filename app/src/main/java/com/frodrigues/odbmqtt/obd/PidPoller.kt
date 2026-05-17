package com.frodrigues.odbmqtt.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class PidReading(
    val pid: Int,
    val value: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class PidPoller(
    private val executor: ObdCommandExecutor,
    private val fastPids: Set<Int>,
    private val slowPids: Set<Int>
) {
    private val slowEvery = SLOW_INTERVAL_SECONDS / FAST_INTERVAL_SECONDS

    fun readings(): Flow<PidReading> = flow {
        var cycle = 0
        while (true) {
            val cycleStart = System.currentTimeMillis()

            for (pid in fastPids) {
                val response = executor.sendCommand("01${pid.toString(16).padStart(2, '0').uppercase()}")
                PidParser.parse(pid, response)?.let { emit(PidReading(pid, it)) }
            }

            if (cycle % slowEvery == 0) {
                for (pid in slowPids) {
                    val response = executor.sendCommand("01${pid.toString(16).padStart(2, '0').uppercase()}")
                    PidParser.parse(pid, response)?.let { emit(PidReading(pid, it)) }
                }
            }

            cycle++
            val remaining = FAST_INTERVAL_SECONDS * 1000L - (System.currentTimeMillis() - cycleStart)
            if (remaining > 0) delay(remaining)
        }
    }

    companion object {
        const val FAST_INTERVAL_SECONDS = 1
        const val SLOW_INTERVAL_SECONDS = 30
        const val FAST_PID_LIMIT = 10
    }
}
