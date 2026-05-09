package com.frodrigues.jaecoo.obd

import org.junit.Assert.*
import org.junit.Test

class ObdResponseParserTest {

    @Test
    fun `extracts data bytes from response with headers`() {
        val bytes = ObdResponseParser.extractDataBytes("7E8 04 41 0C 0F A0", 0x0C)
        assertNotNull(bytes)
        assertEquals(2, bytes!!.size)
        assertEquals(0x0F, bytes[0].toInt() and 0xFF)
        assertEquals(0xA0, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `extracts data bytes from response without headers`() {
        val bytes = ObdResponseParser.extractDataBytes("41 0C 0F A0", 0x0C)
        assertNotNull(bytes)
        assertEquals(2, bytes!!.size)
        assertEquals(0x0F, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `extracts data bytes from single-byte pid`() {
        val bytes = ObdResponseParser.extractDataBytes("7E8 03 41 0D 3C", 0x0D)
        assertNotNull(bytes)
        assertEquals(1, bytes!!.size)
        assertEquals(0x3C, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `returns null for NO DATA response`() {
        assertNull(ObdResponseParser.extractDataBytes("NO DATA", 0x0C))
    }

    @Test
    fun `returns null for ERROR response`() {
        assertNull(ObdResponseParser.extractDataBytes("ERROR", 0x0C))
    }

    @Test
    fun `returns null for empty response`() {
        assertNull(ObdResponseParser.extractDataBytes("", 0x0C))
    }

    @Test
    fun `returns null when pid not found in response`() {
        assertNull(ObdResponseParser.extractDataBytes("41 0D 3C", 0x0C))
    }

    @Test
    fun `handles leading and trailing whitespace and newlines`() {
        val bytes = ObdResponseParser.extractDataBytes("\r\n41 0D 3C\r\n", 0x0D)
        assertNotNull(bytes)
        assertEquals(0x3C, bytes!![0].toInt() and 0xFF)
    }

    @Test
    fun `returns null for whitespace-only response`() {
        assertNull(ObdResponseParser.extractDataBytes("   \r\n  ", 0x0C))
    }

    @Test
    fun `returns null for UNABLE TO CONNECT response`() {
        assertNull(ObdResponseParser.extractDataBytes("UNABLE TO CONNECT", 0x0C))
    }

    @Test
    fun `returns null for question mark response`() {
        assertNull(ObdResponseParser.extractDataBytes("?", 0x0C))
    }
}
