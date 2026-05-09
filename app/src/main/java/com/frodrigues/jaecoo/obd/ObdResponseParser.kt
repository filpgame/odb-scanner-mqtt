package com.frodrigues.jaecoo.obd

object ObdResponseParser {

    fun extractDataBytes(response: String, pid: Int): ByteArray? {
        val upper = response.uppercase().replace(Regex("[\\s\\r\\n]+"), "")
        if (upper.isBlank() ||
            upper.contains("NODATA") ||
            upper.contains("ERROR") ||
            upper.contains("UNABLE") ||
            upper.contains("?")) return null

        val pidHex = pid.toString(16).padStart(2, '0').uppercase()
        val marker = "41$pidHex"
        val idx = upper.indexOf(marker)
        if (idx == -1) return null

        val dataHex = upper.substring(idx + marker.length)
        return dataHex.chunked(2)
            .take(8)
            .mapNotNull { it.toIntOrNull(16)?.toByte() }
            .toByteArray()
            .takeIf { it.isNotEmpty() }
    }
}
