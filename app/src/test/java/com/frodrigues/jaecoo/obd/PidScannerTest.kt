package com.frodrigues.jaecoo.obd

import com.frodrigues.jaecoo.bluetooth.FakeTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PidScannerTest {

    @Test
    fun `scan returns correct pids from single bitmask`() = runTest {
        // 0100 response: BE 3F A8 13
        // BE = 1011 1110 → PIDs 01,03,04,05,06,07 supported
        // Last bit of 0x13 (0001 0011) is 1 → PID 0x20 supported (continue scan)
        val transport = FakeTransport(
            responses = mapOf(
                "0100" to "7E8 06 41 00 BE 3F A8 13",
                "0120" to "NO DATA"
            )
        )
        transport.connect()
        val executor = ObdCommandExecutor(transport)
        val scanner = PidScanner(executor)
        val supported = scanner.scan()

        assertTrue(0x01 in supported)
        assertFalse(0x02 in supported)
        assertTrue(0x03 in supported)
        assertTrue(0x04 in supported)
        assertTrue(0x05 in supported)
        assertFalse(0x20 in supported)
    }

    @Test
    fun `scan stops when support pid response is NO DATA`() = runTest {
        val transport = FakeTransport(
            responses = mapOf("0100" to "NO DATA")
        )
        transport.connect()
        val executor = ObdCommandExecutor(transport)
        val scanner = PidScanner(executor)
        val supported = scanner.scan()

        assertTrue(supported.isEmpty())
        assertEquals(listOf("0100"), transport.sentCommands)
    }

    @Test
    fun `scan does not include support pids themselves`() = runTest {
        val transport = FakeTransport(
            responses = mapOf(
                "0100" to "7E8 06 41 00 FF FF FF FF",
                "0120" to "NO DATA"
            )
        )
        transport.connect()
        val scanner = PidScanner(ObdCommandExecutor(transport))
        val supported = scanner.scan()

        assertFalse(0x20 in supported)
        assertFalse(0x00 in supported)
        assertTrue(0x01 in supported)
        assertTrue(0x1F in supported)
    }
}
