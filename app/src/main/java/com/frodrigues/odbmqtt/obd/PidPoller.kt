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
    private val supportedPids: Set<Int>,
    private val intervalSeconds: Int
) {

    fun readings(): Flow<PidReading> = flow {
        while (true) {
            val cycleStart = System.currentTimeMillis()

            for (pid in supportedPids) {
                val pidHex = pid.toString(16).padStart(2, '0').uppercase()
                val response = executor.sendCommand("01$pidHex")
                val value = PidParser.parse(pid, response)
                if (value != null) {
                    emit(PidReading(pid, value))
                }
            }

            val elapsed = System.currentTimeMillis() - cycleStart
            val remaining = intervalSeconds * 1000L - elapsed
            if (remaining > 0) delay(remaining)
        }
    }
}
