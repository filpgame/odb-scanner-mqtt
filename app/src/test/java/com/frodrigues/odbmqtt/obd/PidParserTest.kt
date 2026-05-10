package com.frodrigues.odbmqtt.obd

import org.junit.Assert.*
import org.junit.Test

class PidParserTest {

    @Test
    fun `parses RPM correctly`() {
        // 3E 80 → (62 * 256 + 128) / 4 = 16000 / 4 = 4000 rpm
        val result = PidParser.parse(0x0C, "7E8 04 41 0C 3E 80")
        assertNotNull(result)
        assertEquals(4000.0, result!!, 0.01)
    }

    @Test
    fun `parses speed correctly`() {
        val result = PidParser.parse(0x0D, "7E8 03 41 0D 3C")
        assertNotNull(result)
        assertEquals(60.0, result!!, 0.01)
    }

    @Test
    fun `parses coolant temperature correctly`() {
        // 5A → 90 - 40 = 50 °C
        val result = PidParser.parse(0x05, "41 05 5A")
        assertNotNull(result)
        assertEquals(50.0, result!!, 0.01)
    }

    @Test
    fun `parses fuel level correctly`() {
        // 80 → 100 * 128 / 255 ≈ 50.2%
        val result = PidParser.parse(0x2F, "41 2F 80")
        assertNotNull(result)
        assertEquals(50.196, result!!, 0.01)
    }

    @Test
    fun `parses control module voltage correctly`() {
        // 39 AC → 14764 / 1000 = 14.764 V
        val result = PidParser.parse(0x42, "41 42 39 AC")
        assertNotNull(result)
        assertEquals(14.764, result!!, 0.001)
    }

    @Test
    fun `parses odometer correctly`() {
        // 00 00 27 10 → 10000 / 10 = 1000 km
        val result = PidParser.parse(0xA6, "41 A6 00 00 27 10")
        assertNotNull(result)
        assertEquals(1000.0, result!!, 0.01)
    }

    @Test
    fun `returns null for NO DATA`() {
        assertNull(PidParser.parse(0x0C, "NO DATA"))
    }

    @Test
    fun `returns null for unknown pid`() {
        assertNull(PidParser.parse(0xFF, "41 FF 00"))
    }
}
