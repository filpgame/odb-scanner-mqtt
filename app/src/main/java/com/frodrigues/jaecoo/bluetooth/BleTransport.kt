package com.frodrigues.jaecoo.bluetooth

import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID

class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
    serviceUuid: String = "0000fff0-0000-1000-8000-00805f9b34fb"
) : BluetoothTransport {

    private val serviceUUID = UUID.fromString(serviceUuid)
    private val writeUUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    private val notifyUUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    private val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val responseChannel = Channel<String>(Channel.BUFFERED)
    private val responseBuffer = StringBuilder()
    private val connectLatch = CompletableDeferred<Unit>()

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else {
                _isConnected.value = false
                if (!connectLatch.isCompleted) {
                    connectLatch.completeExceptionally(IOException("BLE disconnected, status=$status"))
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connectLatch.completeExceptionally(IOException("Service discovery failed: $status"))
                return
            }
            val service = gatt.getService(serviceUUID)
            val notifyChar = service?.getCharacteristic(notifyUUID)
            if (notifyChar == null) {
                connectLatch.completeExceptionally(IOException("Notify characteristic not found"))
                return
            }
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(cccdUUID)
            descriptor?.let {
                gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
            _isConnected.value = true
            connectLatch.complete(Unit)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val chunk = value.toString(Charsets.UTF_8)
            responseBuffer.append(chunk)
            if (responseBuffer.contains('>')) {
                val response = responseBuffer.toString().substringBefore('>').trim()
                responseBuffer.clear()
                responseChannel.trySend(response)
            }
        }
    }

    override suspend fun connect() {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        withTimeout(10_000) { connectLatch.await() }
    }

    override suspend fun disconnect() {
        _isConnected.value = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    override suspend fun sendCommand(command: String): String {
        val g = gatt ?: throw IOException("Not connected")
        val service = g.getService(serviceUUID) ?: throw IOException("Service not found")
        val writeChar = service.getCharacteristic(writeUUID) ?: throw IOException("Write char not found")
        g.writeCharacteristic(writeChar, "$command\r".toByteArray(Charsets.UTF_8), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        return withTimeout(5_000) { responseChannel.receive() }
    }
}
