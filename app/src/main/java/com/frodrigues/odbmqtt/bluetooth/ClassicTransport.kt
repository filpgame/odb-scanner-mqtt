package com.frodrigues.odbmqtt.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ClassicTransport(private val device: BluetoothDevice) : BluetoothTransport {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        s.connect()
        socket = s
        inputStream = s.inputStream
        outputStream = s.outputStream
        _isConnected.value = true
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        _isConnected.value = false
        runCatching { socket?.close() }
        socket = null
        inputStream = null
        outputStream = null
    }

    override suspend fun sendCommand(command: String): String = withTimeout(5_000) {
        withContext(Dispatchers.IO) {
            val out = outputStream ?: throw IOException("Not connected")
            val inp = inputStream ?: throw IOException("Not connected")
            out.write("$command\r".toByteArray(Charsets.UTF_8))
            out.flush()
            readUntilPrompt(inp)
        }
    }

    private fun readUntilPrompt(inp: InputStream): String {
        val buffer = StringBuilder()
        val b = ByteArray(1)
        while (true) {
            val read = inp.read(b)
            if (read == -1) throw IOException("Stream closed")
            val ch = b[0].toInt().toChar()
            if (ch == '>') break
            buffer.append(ch)
        }
        return buffer.toString().trim()
    }

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
