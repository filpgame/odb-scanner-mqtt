package com.frodrigues.odbmqtt.obd

object PidParser {

    fun parse(pid: Int, response: String): Double? {
        val bytes = ObdResponseParser.extractDataBytes(response, pid) ?: return null
        val definition = PidRegistry.definitions[pid] ?: return null
        return runCatching { definition.formula(bytes) }.getOrNull()
    }
}
