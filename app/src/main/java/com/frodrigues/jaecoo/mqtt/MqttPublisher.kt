package com.frodrigues.jaecoo.mqtt

import com.frodrigues.jaecoo.settings.AppConfig
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MqttPublisher(private val config: AppConfig) {

    private lateinit var client: Mqtt3AsyncClient

    suspend fun connect() = withContext(Dispatchers.IO) {
        val builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(config.mqttClientId)
            .serverHost(config.mqttHost)
            .serverPort(config.mqttPort)
            .buildAsync()

        client = builder

        val connectBuilder = client.connectWith()
        if (config.mqttUser.isNotBlank()) {
            connectBuilder
                .simpleAuth()
                .username(config.mqttUser)
                .password(config.mqttPassword.toByteArray(Charsets.UTF_8))
                .applySimpleAuth()
        }
        connectBuilder.send().get()
    }

    suspend fun publish(topic: String, payload: String, retain: Boolean = false) =
        withContext(Dispatchers.IO) {
            client.publishWith()
                .topic(topic)
                .payload(payload.toByteArray(Charsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(retain)
                .send()
                .get()
        }

    suspend fun publishEmpty(topic: String) = withContext(Dispatchers.IO) {
        client.publishWith()
            .topic(topic)
            .retain(true)
            .send()
            .get()
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { client.disconnect().get() }
    }
}
