package com.frodrigues.odbmqtt.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface BluetoothTransport {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendCommand(command: String): String
    val isConnected: StateFlow<Boolean>
}

object BluetoothTransportFactory {
    fun create(device: BluetoothDevice, context: Context): BluetoothTransport {
        return when (device.type) {
            BluetoothDevice.DEVICE_TYPE_LE -> BleTransport(context, device)
            else -> ClassicTransport(device)
        }
    }
}
