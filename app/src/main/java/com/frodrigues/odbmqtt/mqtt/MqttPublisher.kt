package com.frodrigues.odbmqtt.mqtt

import com.frodrigues.odbmqtt.settings.AppConfig
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MqttPublisher(private val config: AppConfig) {

    @Volatile private var client: Mqtt3AsyncClient? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        val newClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier(config.mqttClientId)
            .serverHost(config.mqttHost)
            .serverPort(config.mqttPort)
            .buildAsync()

        val connectBuilder = newClient.connectWith()
        if (config.mqttUser.isNotBlank()) {
            connectBuilder
                .simpleAuth()
                .username(config.mqttUser)
                .password(config.mqttPassword.toByteArray(Charsets.UTF_8))
                .applySimpleAuth()
        }
        connectBuilder.send().get()  // throws if connection fails
        client = newClient           // only assigned on success
    }

    suspend fun publish(topic: String, payload: String, retain: Boolean = false) =
        withContext(Dispatchers.IO) {
            val c = client ?: throw IllegalStateException("Not connected")
            c.publishWith()
                .topic(topic)
                .payload(payload.toByteArray(Charsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(retain)
                .send()
                .get()
        }

    suspend fun publishEmpty(topic: String) = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext
        c.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .send()
            .get()
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { client?.disconnect()?.get() }
        client = null
    }
}
