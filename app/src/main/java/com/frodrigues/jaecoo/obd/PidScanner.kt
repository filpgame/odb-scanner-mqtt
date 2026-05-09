package com.frodrigues.jaecoo.obd

class PidScanner(private val executor: ObdCommandExecutor) {

    private val supportPids = listOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0)

    suspend fun scan(): Set<Int> {
        val supported = mutableSetOf<Int>()

        for (supportPid in supportPids) {
            val cmd = "01${supportPid.toString(16).padStart(2, '0').uppercase()}"
            val response = executor.sendCommand(cmd)
            val bytes = ObdResponseParser.extractDataBytes(response, supportPid)
            if (bytes == null || bytes.size < 4) break

            val bitmask = (bytes.u(0) shl 24) or
                          (bytes.u(1) shl 16) or
                          (bytes.u(2) shl 8) or
                          bytes.u(3)

            for (bit in 0..31) {
                if (bitmask and (1 shl (31 - bit)) != 0) {
                    val pid = supportPid + bit + 1
                    if (pid % 0x20 != 0) {
                        supported.add(pid)
                    }
                }
            }

            if ((bitmask and 1) == 0) break
        }

        return supported
    }
}
