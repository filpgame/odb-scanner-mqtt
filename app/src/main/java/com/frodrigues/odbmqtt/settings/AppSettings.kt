package com.frodrigues.odbmqtt.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

fun syncAutoStartToDeStorage(context: Context, enabled: Boolean) {
    context.createDeviceProtectedStorageContext()
        .getSharedPreferences(AppSettings.DE_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppSettings.DE_KEY_AUTO_START, enabled)
        .apply()
}

data class AppConfig(
    val btDeviceMac: String,
    val mqttHost: String,
    val mqttPort: Int,
    val mqttUser: String,
    val mqttPassword: String,
    val deviceName: String,
    val deviceModel: String,
    val deviceManufacturer: String
) {
    val mqttClientId: String get() = "obd2_android_${btDeviceMac.replace(":", "")}"
}

class AppSettings(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val DE_PREFS_NAME = "boot_prefs"
        const val DE_KEY_AUTO_START = "autoStart"

        val BT_DEVICE_MAC = stringPreferencesKey("btDeviceMac")
        val MQTT_HOST = stringPreferencesKey("mqttHost")
        val MQTT_PORT = intPreferencesKey("mqttPort")
        val MQTT_USER = stringPreferencesKey("mqttUser")
        val MQTT_PASSWORD = stringPreferencesKey("mqttPassword")
        val DEVICE_NAME = stringPreferencesKey("deviceName")
        val DEVICE_MODEL = stringPreferencesKey("deviceModel")
        val DEVICE_MANUFACTURER = stringPreferencesKey("deviceManufacturer")
        // Mode 01 PID discovery cache
        val CACHED_PIDS = stringPreferencesKey("cachedPids")
        // User-selected PIDs for MQTT publishing — null/absent = all discovered PIDs
        val SELECTED_PIDS = stringPreferencesKey("selectedPids")
        // Mode 22 PID discovery cache — key:hex PID (4 chars), value:hex bytes
        val CACHED_MODE22_PIDS = stringPreferencesKey("cachedMode22Pids")
        val AUTO_START = booleanPreferencesKey("autoStart")
    }

    val autoStart: Flow<Boolean> = dataStore.data.map { it[AUTO_START] ?: false }

    val btDeviceMac: Flow<String> = dataStore.data.map { it[BT_DEVICE_MAC] ?: "" }
    val mqttHost: Flow<String> = dataStore.data.map { it[MQTT_HOST] ?: "" }
    val mqttPort: Flow<Int> = dataStore.data.map { it[MQTT_PORT] ?: 1883 }
    val mqttUser: Flow<String> = dataStore.data.map { it[MQTT_USER] ?: "" }
    val mqttPassword: Flow<String> = dataStore.data.map { it[MQTT_PASSWORD] ?: "" }
    val deviceName: Flow<String> = dataStore.data.map { it[DEVICE_NAME] ?: "Jaecoo 7" }
    val deviceModel: Flow<String> = dataStore.data.map { it[DEVICE_MODEL] ?: "Jaecoo 7" }
    val deviceManufacturer: Flow<String> = dataStore.data.map { it[DEVICE_MANUFACTURER] ?: "Jaecoo" }

    suspend fun snapshot(): AppConfig = AppConfig(
        btDeviceMac = btDeviceMac.first(),
        mqttHost = mqttHost.first(),
        mqttPort = mqttPort.first(),
        mqttUser = mqttUser.first(),
        mqttPassword = mqttPassword.first(),
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

    // ── Selected PIDs ─────────────────────────────────────────────────────────
    // null = all discovered PIDs are active (default)
    val selectedPids: kotlinx.coroutines.flow.Flow<Set<Int>?> = dataStore.data.map { prefs ->
        val raw = prefs[SELECTED_PIDS] ?: return@map null
        if (raw.isBlank()) null
        else raw.split(",").mapNotNull { it.trim().toIntOrNull(16) }.toSet()
    }

    suspend fun setSelectedPids(pids: Set<Int>?) {
        update {
            if (pids == null) remove(SELECTED_PIDS)
            else this[SELECTED_PIDS] = pids.joinToString(",") { it.toString(16).uppercase() }
        }
    }

    // ── Mode 22 cache ────────────────────────────────────────────────────────
    // Format: "XXYY:AABBCC,XXYY:AABB,..." — pid hex : data bytes hex
    val cachedMode22Pids: kotlinx.coroutines.flow.Flow<Map<Int, ByteArray>> = dataStore.data.map { prefs ->
        val raw = prefs[CACHED_MODE22_PIDS] ?: ""
        if (raw.isBlank()) emptyMap()
        else raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val pid = parts[0].toIntOrNull(16) ?: return@mapNotNull null
            val bytes = parts[1].chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
            pid to bytes
        }.toMap()
    }

    suspend fun saveMode22Cache(pids: Map<Int, ByteArray>) {
        update {
            this[CACHED_MODE22_PIDS] = pids.entries.joinToString(",") { (pid, bytes) ->
                "${pid.toString(16).padStart(4,'0').uppercase()}:${bytes.joinToString("") { "%02X".format(it) }}"
            }
        }
    }

    suspend fun clearMode22Cache() {
        update { remove(CACHED_MODE22_PIDS) }
    }

    suspend fun update(block: MutablePreferences.() -> Unit) {
        dataStore.edit { block(it) }
    }
}
