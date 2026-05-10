package com.frodrigues.odbmqtt.obd

import com.frodrigues.odbmqtt.bluetooth.BluetoothTransport
import kotlinx.coroutines.delay
import java.io.IOException

class ObdCommandExecutor(private val transport: BluetoothTransport) {

    suspend fun initialize() {
        val atzResponse = transport.sendCommand("ATZ")
        delay(1000)
        if (!atzResponse.contains("ELM", ignoreCase = true) &&
            !atzResponse.contains("OK", ignoreCase = true)) {
            throw IOException("ELM327 not detected. Got: $atzResponse")
        }
        transport.sendCommand("ATE0")   
        transport.sendCommand("ATL0")
        transport.sendCommand("ATH1")
        transport.sendCommand("ATSP6")
        transport.sendCommand("ATAT1")
    }

    suspend fun sendCommand(command: String): String = transport.sendCommand(command)
}
