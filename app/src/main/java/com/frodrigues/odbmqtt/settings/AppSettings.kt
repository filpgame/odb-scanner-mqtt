package com.frodrigues.odbmqtt.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppConfig(
    val btDeviceMac: String,
    val mqttHost: String,
    val mqttPort: Int,
    val mqttUser: String,
    val mqttPassword: String,
    val pollIntervalSeconds: Int,
    val deviceName: String,
    val deviceModel: String,
    val deviceManufacturer: String
) {
    val mqttClientId: String get() = "obd2_android_${btDeviceMac.replace(":", "")}"
}

class AppSettings(private val dataStore: DataStore<Preferences>) {

    companion object {
        val BT_DEVICE_MAC = stringPreferencesKey("btDeviceMac")
        val MQTT_HOST = stringPreferencesKey("mqttHost")
        val MQTT_PORT = intPreferencesKey("mqttPort")
        val MQTT_USER = stringPreferencesKey("mqttUser")
        val MQTT_PASSWORD = stringPreferencesKey("mqttPassword")
        val POLL_INTERVAL_SECONDS = intPreferencesKey("pollIntervalSeconds")
        val DEVICE_NAME = stringPreferencesKey("deviceName")
        val DEVICE_MODEL = stringPreferencesKey("deviceModel")
        val DEVICE_MANUFACTURER = stringPreferencesKey("deviceManufacturer")
        // PID discovery cache
        val CACHED_PIDS = stringPreferencesKey("cachedPids")
    }

    val btDeviceMac: Flow<String> = dataStore.data.map { it[BT_DEVICE_MAC] ?: "" }
    val mqttHost: Flow<String> = dataStore.data.map { it[MQTT_HOST] ?: "" }
    val mqttPort: Flow<Int> = dataStore.data.map { it[MQTT_PORT] ?: 1883 }
    val mqttUser: Flow<String> = dataStore.data.map { it[MQTT_USER] ?: "" }
    val mqttPassword: Flow<String> = dataStore.data.map { it[MQTT_PASSWORD] ?: "" }
    val pollIntervalSeconds: Flow<Int> = dataStore.data.map { it[POLL_INTERVAL_SECONDS] ?: 5 }
    val deviceName: Flow<String> = dataStore.data.map { it[DEVICE_NAME] ?: "Jaecoo 7" }
    val deviceModel: Flow<String> = dataStore.data.map { it[DEVICE_MODEL] ?: "Jaecoo 7" }
    val deviceManufacturer: Flow<String> = dataStore.data.map { it[DEVICE_MANUFACTURER] ?: "Jaecoo" }

    suspend fun snapshot(): AppConfig = AppConfig(
        btDeviceMac = btDeviceMac.first(),
        mqttHost = mqttHost.first(),
        mqttPort = mqttPort.first(),
        mqttUser = mqttUser.first(),
        mqttPassword = mqttPassword.first(),
        pollIntervalSeconds = pollIntervalSeconds.first(),
        deviceName = deviceName.first(),
        deviceModel = deviceModel.first(),
        deviceManufacturer = deviceManufacturer.first()
    )

    val cachedPids: Flow<Set<Int>> = dataStore.data.map { prefs ->
        val raw = prefs[CACHED_PIDS] ?: ""
        if (raw.isBlank()) emptySet()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull(16) }.toSet()
    }

    suspend fun savePidCache(pids: Set<Int>) {
        update { this[CACHED_PIDS] = pids.joinToString(",") { it.toString(16).uppercase() } }
    }

    suspend fun clearPidCache() {
        update { remove(CACHED_PIDS) }
    }

    suspend fun update(block: MutablePreferences.() -> Unit) {
        dataStore.edit { block(it) }
    }
}
