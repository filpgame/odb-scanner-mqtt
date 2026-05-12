package com.frodrigues.odbmqtt.obd

object PidParser {

    fun parse(pid: Int, response: String): Double? {
        val bytes = ObdResponseParser.extractDataBytes(response, pid) ?: return null
        return runCatching { PidRegistry.getOrUnknown(pid).formula(bytes) }.getOrNull()
    }
}
