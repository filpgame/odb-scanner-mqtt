package com.frodrigues.odbmqtt.mqtt

import com.frodrigues.odbmqtt.obd.PidDefinition
import com.frodrigues.odbmqtt.obd.PidRegistry
import com.frodrigues.odbmqtt.settings.AppConfig

class HaDiscoveryPublisher(
    private val publish: suspend (topic: String, payload: String) -> Unit,
    private val config: AppConfig
) {

    suspend fun publishDiscovery(supportedPids: Set<Int>, mac: String) {
        supportedPids.forEach { pid ->
            val def = PidRegistry.definitions[pid] ?: return@forEach
            val pidHex = pid.toString(16).padStart(2, '0').uppercase()
            val topic = "homeassistant/sensor/obd2_$mac/$pidHex/config"
            val payload = buildPayload(def, mac, pidHex)
            publish(topic, payload)
        }
    }

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun jsonString(value: String): String = "\"${value.jsonEscape()}\""

    private fun buildPayload(def: PidDefinition, mac: String, pidHex: String): String {
        val deviceClassEntry = def.haDeviceClass
            ?.let { ",\"device_class\":${jsonString(it)}" }
            ?: ""
        val device = buildString {
            append("{")
            append("\"identifiers\":[${jsonString("obd2_$mac")}]")
            append(",\"name\":${jsonString(config.deviceName)}")
            append(",\"model\":${jsonString(config.deviceModel)}")
            append(",\"manufacturer\":${jsonString(config.deviceManufacturer)}")
            append("}")
        }
        return buildString {
            append("{")
            append("\"name\":${jsonString("${config.deviceName} ${def.name}")}")
            append(",\"state_topic\":${jsonString("obd2/$mac/$pidHex/state")}")
            append(",\"unit_of_measurement\":${jsonString(def.unit)}")
            append(",\"unique_id\":${jsonString("${mac}_$pidHex")}")
            append(deviceClassEntry)
            append(",\"device\":$device")
            append("}")
        }
    }
}
