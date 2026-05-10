package com.frodrigues.odbmqtt.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeTransport(
    private val responses: Map<String, String> = emptyMap()
) : BluetoothTransport {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val sentCommands = mutableListOf<String>()

    override suspend fun connect() {
        _isConnected.value = true
    }

    override suspend fun disconnect() {
        _isConnected.value = false
    }

    override suspend fun sendCommand(command: String): String {
        sentCommands.add(command.trim())
        return responses[command.trim()] ?: "NO DATA"
    }
}
