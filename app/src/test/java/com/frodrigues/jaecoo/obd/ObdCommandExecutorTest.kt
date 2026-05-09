package com.frodrigues.jaecoo.obd

import com.frodrigues.jaecoo.bluetooth.FakeTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ObdCommandExecutorTest {

    @Test
    fun `initialize sends all AT commands in order`() = runTest {
        val transport = FakeTransport(
            responses = mapOf(
                "ATZ" to "ELM327 v1.5",
                "ATE0" to "OK",
                "ATL0" to "OK",
                "ATH1" to "OK",
                "ATSP6" to "OK",
                "ATAT1" to "OK"
            )
        )
        val executor = ObdCommandExecutor(transport)
        executor.initialize()

        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATH1", "ATSP6", "ATAT1"), transport.sentCommands)
    }

    @Test
    fun `sendCommand delegates to transport`() = runTest {
        val transport = FakeTransport(responses = mapOf("010C" to "7E8 04 41 0C 0F A0"))
        val executor = ObdCommandExecutor(transport)
        transport.connect()

        val response = executor.sendCommand("010C")
        assertEquals("7E8 04 41 0C 0F A0", response)
    }
}
