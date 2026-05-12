package com.frodrigues.odbmqtt.obd

import android.util.Log
import com.frodrigues.odbmqtt.settings.AppSettings
import kotlinx.coroutines.flow.first

/**
 * Brute-force scanner for OBD2 Mode 22 (manufacturer-specific extended diagnostics).
 *
 * Mode 22 uses 2-byte PIDs. Request: "22 XX YY", response: "62 XX YY data...".
 * Negative response: "7F 22 XX" (service not supported / conditions not met).
 *
 * Scans configured ranges and caches results. Raw bytes are logged for reverse-engineering.
 */
class Mode22Scanner(
    private val executor: ObdCommandExecutor,
    private val settings: AppSettings,
    private val onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> }
) {
    // Ranges to brute-force.
    // NOTE (Jaecoo 7 findings):
    //   0x0001-0x00FF → all UNABLE TO CONNECT on engine ECU (0x7E8)
    //   0xF400-0xF4FF → SAE J2190 extended — mirrors Mode 01 exactly, no new data
    //   To get proprietary data, use ATSH to target other ECUs (transmission=0x7E1, body=0x7E5)
    private val scanRanges = listOf(
        0xF400..0xF4FF,   // SAE J2190 extended standard range
    )

    suspend fun scan(): Map<Int, ByteArray> {
        val cached = settings.cachedMode22Pids.first()
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Mode22 cache hit: ${cached.size} PIDs")
            return cached
        }
        return bruteForce()
    }

    private suspend fun bruteForce(): Map<Int, ByteArray> {
        val found = mutableMapOf<Int, ByteArray>()
        val allPids = scanRanges.flatMap { it.toList() }
        val total = allPids.size

        Log.d(TAG, "Mode22 brute-force: probing $total PIDs across ${scanRanges.size} ranges")

        allPids.forEachIndexed { index, pid ->
            onProgress(index + 1, total)

            val high = (pid shr 8) and 0xFF
            val low = pid and 0xFF
            val cmd = "22${high.toString(16).padStart(2, '0').uppercase()}${low.toString(16).padStart(2, '0').uppercase()}"

            val response = executor.sendCommand(cmd)
            val bytes = extractMode22Bytes(response, pid)

            if (bytes != null && bytes.isNotEmpty()) {
                found[pid] = bytes
                val known = Mode22Registry.definitions[pid]
                val name = known?.name ?: "Unknown"
                val hexBytes = bytes.take(8).joinToString(" ") { "%02X".format(it) }
                Log.d(TAG, "✓ 0x${pid.toString(16).padStart(4,'0').uppercase()} $name  [$hexBytes]")
            } else if (response.contains("7F", ignoreCase = true) &&
                       !response.contains("NO DATA", ignoreCase = true)) {
                // Negative response — service/PID exists but conditions not met
                Log.v(TAG, "~ 0x${pid.toString(16).padStart(4,'0').uppercase()} NRC: ${response.trim().take(30)}")
            } else {
                Log.v(TAG, "✗ 0x${pid.toString(16).padStart(4,'0').uppercase()}: ${response.trim().take(20)}")
            }
        }

        Log.d(TAG, "Mode22 done: ${found.size}/$total PIDs responded with data")
        Log.d(TAG, "Known: ${found.keys.count { Mode22Registry.definitions.containsKey(it) }}")
        Log.d(TAG, "Unknown raw: ${found.keys.count { !Mode22Registry.definitions.containsKey(it) }}")

        // Log all found PIDs for analysis
        found.forEach { (pid, bytes) ->
            val pidStr = pid.toString(16).padStart(4, '0').uppercase()
            val hexBytes = bytes.joinToString(" ") { "%02X".format(it) }
            val possibleTemp = if (bytes.isNotEmpty()) bytes[0].toInt() and 0xFF else 0
            val asMinus40 = possibleTemp - 40
            val asUint16 = if (bytes.size >= 2) (bytes[0].toInt() and 0xFF) * 256 + (bytes[1].toInt() and 0xFF) else -1
            Log.i(TAG, "ANALYSIS 0x$pidStr: raw=[$hexBytes] | byte0=${possibleTemp} | byte0-40=${asMinus40}°C | uint16=${asUint16}")
        }

        settings.saveMode22Cache(found)
        return found
    }

    companion object {
        private const val TAG = "Mode22Scanner"

        fun extractMode22Bytes(response: String, pid: Int): ByteArray? {
            val upper = response.uppercase().replace(Regex("[\\s\\r\\n]+"), "")
            if (upper.isBlank() ||
                upper.contains("NODATA") ||
                upper.contains("ERROR") ||
                upper.contains("UNABLE")) return null

            val marker = "62${pid.toString(16).padStart(4, '0').uppercase()}"
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
}
