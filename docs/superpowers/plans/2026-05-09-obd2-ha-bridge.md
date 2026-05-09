# OBD2 → Home Assistant Bridge — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android background ForegroundService that reads all supported OBD2 PIDs from a Jaecoo 7 via ELM327 Bluetooth (Classic + BLE) and publishes sensor readings to Home Assistant via MQTT Discovery.

**Architecture:** Four layers — `BluetoothTransport` (Classic/BLE abstraction with device at construction), OBD2 Protocol (ELM327 init, PID bitmask scan, round-robin polling, hex parsing), MQTT/HA Discovery (HiveMQ, auto-creates HA entities), `OBDCollectorService` (ForegroundService orchestrator with exponential backoff). Minimal Compose UI for status and configuration.

**Tech Stack:** Kotlin, Jetpack Compose, `LifecycleService`, DataStore Preferences, HiveMQ MQTT Client 1.3.3, Coroutines 1.8.1, Navigation Compose 2.8.5.

---

## File Structure

### New files
```
app/src/main/java/com/frodrigues/jaecoo/
├── bluetooth/
│   ├── BluetoothTransport.kt          interface + BluetoothTransportFactory
│   ├── ClassicTransport.kt            SPP BluetoothSocket implementation
│   └── BleTransport.kt               GATT implementation
├── obd/
│   ├── PidDefinition.kt              data class + typealias PidFormula
│   ├── PidRegistry.kt                object map of all 39 known PIDs
│   ├── ObdResponseParser.kt          extracts data bytes from raw ELM327 response
│   ├── PidParser.kt                  applies formula from PidRegistry to parsed bytes
│   ├── PidScanner.kt                 reads bitmask responses → Set<Int> of supported PIDs
│   ├── ObdCommandExecutor.kt         sends AT init + OBD commands via BluetoothTransport
│   └── PidPoller.kt                  PidReading data class + Flow<PidReading> round-robin loop
├── mqtt/
│   ├── MqttPublisher.kt              HiveMQ Mqtt3AsyncClient wrapper
│   └── HaDiscoveryPublisher.kt       builds + publishes HA MQTT discovery JSON payloads
├── settings/
│   └── AppSettings.kt               DataStore wrapper, AppConfig data class, Context.dataStore ext
├── service/
│   └── OBDCollectorService.kt        ForegroundService orchestrator + ServiceStatus enum
└── ui/
    ├── MainViewModel.kt              AndroidViewModel — exposes ServiceStatus, pidCount, lastUpdate
    ├── MainScreen.kt                 status card + Start/Stop button
    └── SettingsScreen.kt             all config fields: BT picker, MQTT, poll interval, device info
```

### Modified files
```
gradle/libs.versions.toml             add hivemq, datastore, coroutines, navigation, lifecycle-service
app/build.gradle.kts                  add new dependencies
app/src/main/AndroidManifest.xml      add permissions + service + notification declarations
app/src/main/java/com/frodrigues/jaecoo/MainActivity.kt   NavHost + permission request
```

### Test files
```
app/src/test/java/com/frodrigues/jaecoo/
├── bluetooth/
│   └── FakeTransport.kt             in-memory BluetoothTransport for unit tests
├── obd/
│   ├── ObdResponseParserTest.kt
│   ├── PidParserTest.kt
│   └── PidScannerTest.kt
└── mqtt/
    └── HaDiscoveryPublisherTest.kt
```

---

## Task 1: Gradle Dependencies + Manifest Permissions

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Update libs.versions.toml**

Replace the entire file contents:

```toml
[versions]
agp = "9.0.1"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.8.0"
kotlin = "2.0.21"
composeBom = "2024.09.00"
hivemq = "1.3.3"
datastore = "1.1.1"
coroutines = "1.8.1"
navigation = "2.8.5"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
hivemq-mqtt-client = { group = "com.hivemq", name = "hivemq-mqtt-client", version.ref = "hivemq" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Update app/build.gradle.kts dependencies block**

Replace the `dependencies { }` block:

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hivemq.mqtt.client)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 3: Update AndroidManifest.xml**

Replace contents:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Jaecoo">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Jaecoo">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.OBDCollectorService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false" />

    </application>

</manifest>
```

- [ ] **Step 4: Verify build compiles**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (no new source files yet, just dependency resolution)

- [ ] **Step 5: Commit**

```
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "chore: add OBD2/MQTT/DataStore dependencies and BT permissions"
```

---

## Task 2: AppSettings (DataStore)

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/settings/AppSettings.kt`

- [ ] **Step 1: Create AppSettings.kt**

```kotlin
package com.frodrigues.jaecoo.settings

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

    suspend fun update(block: MutablePreferences.() -> Unit) {
        dataStore.edit { block(it) }
    }
}
```

- [ ] **Step 2: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/settings/AppSettings.kt
git commit -m "feat: add AppSettings DataStore with all config keys"
```

---

## Task 3: PidDefinition + PidRegistry

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/obd/PidDefinition.kt`
- Create: `app/src/main/java/com/frodrigues/jaecoo/obd/PidRegistry.kt`

- [ ] **Step 1: Create PidDefinition.kt**

```kotlin
package com.frodrigues.jaecoo.obd

typealias PidFormula = (ByteArray) -> Double

data class PidDefinition(
    val pid: Int,
    val name: String,
    val unit: String,
    val haDeviceClass: String?,
    val formula: PidFormula
)

fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF
```

- [ ] **Step 2: Create PidRegistry.kt**

```kotlin
package com.frodrigues.jaecoo.obd

object PidRegistry {
    val definitions: Map<Int, PidDefinition> = mapOf(
        0x04 to PidDefinition(0x04, "Engine Load", "%", null) { b ->
            b.u(0) / 2.55
        },
        0x05 to PidDefinition(0x05, "Coolant Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x06 to PidDefinition(0x06, "Short Fuel Trim B1", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },
        0x07 to PidDefinition(0x07, "Long Fuel Trim B1", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },
        0x0A to PidDefinition(0x0A, "Fuel Pressure", "kPa", "pressure") { b ->
            3.0 * b.u(0)
        },
        0x0B to PidDefinition(0x0B, "Intake MAP", "kPa", "pressure") { b ->
            b.u(0).toDouble()
        },
        0x0C to PidDefinition(0x0C, "RPM", "rpm", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 4.0
        },
        0x0D to PidDefinition(0x0D, "Speed", "km/h", "speed") { b ->
            b.u(0).toDouble()
        },
        0x0E to PidDefinition(0x0E, "Timing Advance", "°", null) { b ->
            b.u(0) / 2.0 - 64.0
        },
        0x0F to PidDefinition(0x0F, "Intake Air Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x10 to PidDefinition(0x10, "MAF Rate", "g/s", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 100.0
        },
        0x11 to PidDefinition(0x11, "Throttle Position", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x1F to PidDefinition(0x1F, "Run Time", "s", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x21 to PidDefinition(0x21, "Distance w/ MIL", "km", "distance") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x22 to PidDefinition(0x22, "Fuel Rail Pressure", "kPa", "pressure") { b ->
            0.079 * (b.u(0) * 256 + b.u(1))
        },
        0x2C to PidDefinition(0x2C, "Commanded EGR", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x2F to PidDefinition(0x2F, "Fuel Level", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x31 to PidDefinition(0x31, "Distance Since Cleared", "km", "distance") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x33 to PidDefinition(0x33, "Barometric Pressure", "kPa", "pressure") { b ->
            b.u(0).toDouble()
        },
        0x42 to PidDefinition(0x42, "Control Module Voltage", "V", "voltage") { b ->
            (b.u(0) * 256 + b.u(1)) / 1000.0
        },
        0x43 to PidDefinition(0x43, "Absolute Load", "%", null) { b ->
            100.0 * (b.u(0) * 256 + b.u(1)) / 255.0
        },
        0x45 to PidDefinition(0x45, "Relative Throttle", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x46 to PidDefinition(0x46, "Ambient Air Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x49 to PidDefinition(0x49, "Accel Pedal D", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4A to PidDefinition(0x4A, "Accel Pedal E", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4C to PidDefinition(0x4C, "Commanded Throttle", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4D to PidDefinition(0x4D, "Time w/ MIL", "min", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x4E to PidDefinition(0x4E, "Time Since Cleared", "min", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x52 to PidDefinition(0x52, "Ethanol Percent", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5A to PidDefinition(0x5A, "Relative Accel Pedal", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5B to PidDefinition(0x5B, "Hybrid Battery", "%", "battery") { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5C to PidDefinition(0x5C, "Oil Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x5D to PidDefinition(0x5D, "Injection Timing", "°", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 128.0 - 210.0
        },
        0x5E to PidDefinition(0x5E, "Fuel Rate", "L/h", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 20.0
        },
        0x61 to PidDefinition(0x61, "Driver Demand Torque", "%", null) { b ->
            b.u(0) - 125.0
        },
        0x62 to PidDefinition(0x62, "Actual Torque", "%", null) { b ->
            b.u(0) - 125.0
        },
        0x63 to PidDefinition(0x63, "Reference Torque", "N·m", null) { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0xA4 to PidDefinition(0xA4, "Transmission Gear", "ratio", null) { b ->
            (b.u(2) * 256 + b.u(3)) / 1000.0
        },
        0xA6 to PidDefinition(0xA6, "Odometer", "km", "distance") { b ->
            (b.u(0) * 16777216L + b.u(1) * 65536L + b.u(2) * 256L + b.u(3)) / 10.0
        }
    )
}
```

- [ ] **Step 3: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/obd/PidDefinition.kt app/src/main/java/com/frodrigues/jaecoo/obd/PidRegistry.kt
git commit -m "feat: add PidDefinition and PidRegistry with 39 OBD2 PIDs"
```

---

## Task 4: ObdResponseParser + Tests

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/obd/ObdResponseParser.kt`
- Create: `app/src/test/java/com/frodrigues/jaecoo/obd/ObdResponseParserTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/frodrigues/jaecoo/obd/ObdResponseParserTest.kt`:

```kotlin
package com.frodrigues.jaecoo.obd

import org.junit.Assert.*
import org.junit.Test

class ObdResponseParserTest {

    @Test
    fun `extracts data bytes from response with headers`() {
        // ELM327 ATH1 response for PID 0x0C (RPM): "7E8 04 41 0C 0F A0"
        val bytes = ObdResponseParser.extractDataBytes("7E8 04 41 0C 0F A0", 0x0C)
        assertNotNull(bytes)
        assertEquals(2, bytes!!.size)
        assertEquals(0x0F, bytes[0].toInt() and 0xFF)
        assertEquals(0xA0, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `extracts data bytes from response without headers`() {
        val bytes = ObdResponseParser.extractDataBytes("41 0C 0F A0", 0x0C)
        assertNotNull(bytes)
        assertEquals(2, bytes!!.size)
        assertEquals(0x0F, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `extracts data bytes from single-byte pid`() {
        // PID 0x0D (speed): response "7E8 03 41 0D 3C" → speed = 60 km/h
        val bytes = ObdResponseParser.extractDataBytes("7E8 03 41 0D 3C", 0x0D)
        assertNotNull(bytes)
        assertEquals(1, bytes!!.size)
        assertEquals(0x3C, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `returns null for NO DATA response`() {
        assertNull(ObdResponseParser.extractDataBytes("NO DATA", 0x0C))
    }

    @Test
    fun `returns null for ERROR response`() {
        assertNull(ObdResponseParser.extractDataBytes("ERROR", 0x0C))
    }

    @Test
    fun `returns null for empty response`() {
        assertNull(ObdResponseParser.extractDataBytes("", 0x0C))
    }

    @Test
    fun `returns null when pid not found in response`() {
        assertNull(ObdResponseParser.extractDataBytes("41 0D 3C", 0x0C))
    }

    @Test
    fun `handles leading and trailing whitespace and newlines`() {
        val bytes = ObdResponseParser.extractDataBytes("\r\n41 0D 3C\r\n", 0x0D)
        assertNotNull(bytes)
        assertEquals(0x3C, bytes!![0].toInt() and 0xFF)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.ObdResponseParserTest"
```

Expected: FAILED — `ObdResponseParser` does not exist yet

- [ ] **Step 3: Create ObdResponseParser.kt**

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.ObdResponseParserTest"
```

Expected: 8 tests PASSED

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/obd/ObdResponseParser.kt app/src/test/java/com/frodrigues/jaecoo/obd/ObdResponseParserTest.kt
git commit -m "feat: add ObdResponseParser with tests"
```

---

## Task 5: PidParser + Tests

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/obd/PidParser.kt`
- Create: `app/src/test/java/com/frodrigues/jaecoo/obd/PidParserTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/frodrigues/jaecoo/obd/PidParserTest.kt`:

```kotlin
package com.frodrigues.jaecoo.obd

import org.junit.Assert.*
import org.junit.Test

class PidParserTest {

    @Test
    fun `parses RPM correctly`() {
        // 0F A0 → (15 * 256 + 160) / 4 = 4000 rpm
        val result = PidParser.parse(0x0C, "7E8 04 41 0C 0F A0")
        assertNotNull(result)
        assertEquals(4000.0, result!!, 0.01)
    }

    @Test
    fun `parses speed correctly`() {
        // 3C → 60 km/h
        val result = PidParser.parse(0x0D, "7E8 03 41 0D 3C")
        assertNotNull(result)
        assertEquals(60.0, result!!, 0.01)
    }

    @Test
    fun `parses coolant temperature correctly`() {
        // 5A → 90 - 40 = 50 °C
        val result = PidParser.parse(0x05, "41 05 5A")
        assertNotNull(result)
        assertEquals(50.0, result!!, 0.01)
    }

    @Test
    fun `parses fuel level correctly`() {
        // 80 → 100 * 128 / 255 = 50.2%
        val result = PidParser.parse(0x2F, "41 2F 80")
        assertNotNull(result)
        assertEquals(50.196, result!!, 0.01)
    }

    @Test
    fun `parses control module voltage correctly`() {
        // 39 AC → (14764) / 1000 = 14.764 V
        val result = PidParser.parse(0x42, "41 42 39 AC")
        assertNotNull(result)
        assertEquals(14.764, result!!, 0.001)
    }

    @Test
    fun `parses odometer correctly`() {
        // 00 00 27 10 → 10000 / 10 = 1000 km
        val result = PidParser.parse(0xA6, "41 A6 00 00 27 10")
        assertNotNull(result)
        assertEquals(1000.0, result!!, 0.01)
    }

    @Test
    fun `returns null for NO DATA`() {
        assertNull(PidParser.parse(0x0C, "NO DATA"))
    }

    @Test
    fun `returns null for unknown pid`() {
        // PID 0xFF is not in the registry
        assertNull(PidParser.parse(0xFF, "41 FF 00"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.PidParserTest"
```

Expected: FAILED — `PidParser` does not exist yet

- [ ] **Step 3: Create PidParser.kt**

```kotlin
package com.frodrigues.jaecoo.obd

object PidParser {

    fun parse(pid: Int, response: String): Double? {
        val bytes = ObdResponseParser.extractDataBytes(response, pid) ?: return null
        val definition = PidRegistry.definitions[pid] ?: return null
        return runCatching { definition.formula(bytes) }.getOrNull()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.PidParserTest"
```

Expected: 8 tests PASSED

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/obd/PidParser.kt app/src/test/java/com/frodrigues/jaecoo/obd/PidParserTest.kt
git commit -m "feat: add PidParser with formula application and tests"
```

---

## Task 6: BluetoothTransport Interface + FakeTransport

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/bluetooth/BluetoothTransport.kt`
- Create: `app/src/test/java/com/frodrigues/jaecoo/bluetooth/FakeTransport.kt`

- [ ] **Step 1: Create BluetoothTransport.kt**

Note: `connect()` takes no arguments — implementations receive the `BluetoothDevice` at construction time. This keeps `FakeTransport` free of Android API dependencies.

```kotlin
package com.frodrigues.jaecoo.bluetooth

import kotlinx.coroutines.flow.StateFlow

interface BluetoothTransport {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendCommand(command: String): String
    val isConnected: StateFlow<Boolean>
}
```

- [ ] **Step 2: Create FakeTransport.kt** (test helper)

```kotlin
package com.frodrigues.jaecoo.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeTransport(
    private val responses: Map<String, String> = emptyMap()
) : BluetoothTransport {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val sentCommands = mutableListOf<String>()

    override suspend fun connect() {
        _isConnected.value = true
    }

    override suspend fun disconnect() {
        _isConnected.value = false
    }

    override suspend fun sendCommand(command: String): String {
        sentCommands.add(command.trim())
        return responses[command.trim()] ?: "NO DATA"
    }
}
```

- [ ] **Step 3: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/bluetooth/BluetoothTransport.kt app/src/test/java/com/frodrigues/jaecoo/bluetooth/FakeTransport.kt
git commit -m "feat: add BluetoothTransport interface and FakeTransport for tests"
```

---

## Task 7: ObdCommandExecutor + Tests

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/obd/ObdCommandExecutor.kt`
- Create: `app/src/test/java/com/frodrigues/jaecoo/obd/ObdCommandExecutorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/frodrigues/jaecoo/obd/ObdCommandExecutorTest.kt`:

```kotlin
package com.frodrigues.jaecoo.obd

import com.frodrigues.jaecoo.bluetooth.FakeTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ObdCommandExecutorTest {

    @Test
    fun `initialize sends all AT commands in order`() = runTest {
        val transport = FakeTransport(
            responses = mapOf(
                "ATZ" to "ELM327 v1.5",
                "ATE0" to "OK",
                "ATL0" to "OK",
                "ATH1" to "OK",
                "ATSP6" to "OK",
                "ATAT1" to "OK"
            )
        )
        val executor = ObdCommandExecutor(transport)
        executor.initialize()

        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATH1", "ATSP6", "ATAT1"), transport.sentCommands)
    }

    @Test
    fun `sendCommand delegates to transport`() = runTest {
        val transport = FakeTransport(responses = mapOf("010C" to "7E8 04 41 0C 0F A0"))
        val executor = ObdCommandExecutor(transport)
        transport.connect()

        val response = executor.sendCommand("010C")
        assertEquals("7E8 04 41 0C 0F A0", response)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.ObdCommandExecutorTest"
```

Expected: FAILED — `ObdCommandExecutor` does not exist yet

- [ ] **Step 3: Create ObdCommandExecutor.kt**

```kotlin
package com.frodrigues.jaecoo.obd

import com.frodrigues.jaecoo.bluetooth.BluetoothTransport
import kotlinx.coroutines.delay

class ObdCommandExecutor(private val transport: BluetoothTransport) {

    suspend fun initialize() {
        transport.sendCommand("ATZ")
        delay(1000)
        transport.sendCommand("ATE0")
        transport.sendCommand("ATL0")
        transport.sendCommand("ATH1")
        transport.sendCommand("ATSP6")
        transport.sendCommand("ATAT1")
    }

    suspend fun sendCommand(command: String): String = transport.sendCommand(command)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.ObdCommandExecutorTest"
```

Expected: 2 tests PASSED

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/obd/ObdCommandExecutor.kt app/src/test/java/com/frodrigues/jaecoo/obd/ObdCommandExecutorTest.kt
git commit -m "feat: add ObdCommandExecutor with ELM327 init sequence"
```

---

## Task 8: PidScanner + Tests

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/obd/PidScanner.kt`
- Create: `app/src/test/java/com/frodrigues/jaecoo/obd/PidScannerTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/frodrigues/jaecoo/obd/PidScannerTest.kt`:

```kotlin
package com.frodrigues.jaecoo.obd

import com.frodrigues.jaecoo.bluetooth.FakeTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PidScannerTest {

    // Response for "0100": supported PIDs 01-20
    // BE 3F A8 13 = 1011 1110 0011 1111 1010 1000 0001 0011
    // PID 01 = bit31 = 1 → supported
    // PID 02 = bit30 = 0 → not supported
    // ...
    // Last bit (bit0) = 1 → PID 0x20 supported, continue scanning
    @Test
    fun `scan returns correct pids from single bitmask`() = runTest {
        val transport = FakeTransport(
            responses = mapOf(
                "0100" to "7E8 06 41 00 BE 3F A8 13",
                // Last bit of 0100 response is 1 (0x13 = ...0001 0011, bit0 = 1) → 0x20 supported
                "0120" to "NO DATA"  // stop scanning
            )
        )
        transport.connect()
        val executor = ObdCommandExecutor(transport)
        val scanner = PidScanner(executor)
        val supported = scanner.scan()

        // BE = 1011 1110 → PIDs 01,03,04,05,06,07 supported (bit7=1,bit5=1,bit4=1,bit3=1,bit2=1,bit1=1)
        assertTrue(0x01 in supported)
        assertFalse(0x02 in supported)
        assertTrue(0x03 in supported)
        assertTrue(0x04 in supported)
        assertTrue(0x05 in supported)
        // 0x20 is a support PID, never in the result
        assertFalse(0x20 in supported)
    }

    @Test
    fun `scan stops when support pid response is NO DATA`() = runTest {
        val transport = FakeTransport(
            responses = mapOf("0100" to "NO DATA")
        )
        transport.connect()
        val executor = ObdCommandExecutor(transport)
        val scanner = PidScanner(executor)
        val supported = scanner.scan()

        assertTrue(supported.isEmpty())
        // Only 0100 was sent, not 0120
        assertEquals(listOf("0100"), transport.sentCommands)
    }

    @Test
    fun `scan does not include the support pids themselves`() = runTest {
        // FF FF FF FF → all bits set including last (PID 0x20 supported)
        val transport = FakeTransport(
            responses = mapOf(
                "0100" to "7E8 06 41 00 FF FF FF FF",
                "0120" to "NO DATA"
            )
        )
        transport.connect()
        val scanner = PidScanner(ObdCommandExecutor(transport))
        val supported = scanner.scan()

        // 0x20 is the support PID for range 0x21-0x40, should not be in supported set
        assertFalse(0x20 in supported)
        assertFalse(0x00 in supported)
        // But regular PIDs should be there
        assertTrue(0x01 in supported)
        assertTrue(0x1F in supported)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.PidScannerTest"
```

Expected: FAILED — `PidScanner` does not exist yet

- [ ] **Step 3: Create PidScanner.kt**

The algorithm: for each support PID (0x00, 0x20, 0x40, …), send `01XX`, parse 4-byte bitmask. Bit 31 (MSB of byte A) → PID (base+1), bit 30 → PID (base+2), … bit 0 (LSB of byte D) → PID (base+32). Support PIDs (multiples of 0x20) are never added to the result. Stop if response is null or if last bit is 0 (meaning next support PID is not supported).

```kotlin
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

            // Last bit set means next support PID range exists
            if (bitmask and 1 == 0) break
        }

        return supported
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.obd.PidScannerTest"
```

Expected: 3 tests PASSED

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/obd/PidScanner.kt app/src/test/java/com/frodrigues/jaecoo/obd/PidScannerTest.kt
git commit -m "feat: add PidScanner with bitmask discovery and tests"
```

---

## Task 9: PidPoller

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/obd/PidPoller.kt`

No unit test for PidPoller — it is a thin orchestration loop tested as part of the service integration. The logic (parsing, scanning) is already covered by other tests.

- [ ] **Step 1: Create PidPoller.kt**

```kotlin
package com.frodrigues.jaecoo.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class PidReading(
    val pid: Int,
    val value: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class PidPoller(
    private val executor: ObdCommandExecutor,
    private val supportedPids: Set<Int>,
    private val intervalSeconds: Int
) {

    fun readings(): Flow<PidReading> = flow {
        while (true) {
            val cycleStart = System.currentTimeMillis()

            for (pid in supportedPids) {
                val pidHex = pid.toString(16).padStart(2, '0').uppercase()
                val response = executor.sendCommand("01$pidHex")
                val value = PidParser.parse(pid, response)
                if (value != null) {
                    emit(PidReading(pid, value))
                }
            }

            val elapsed = System.currentTimeMillis() - cycleStart
            val remaining = intervalSeconds * 1000L - elapsed
            if (remaining > 0) delay(remaining)
        }
    }
}
```

- [ ] **Step 2: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/obd/PidPoller.kt
git commit -m "feat: add PidPoller Flow with configurable interval"
```

---

## Task 10: ClassicTransport (SPP Bluetooth)

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/bluetooth/ClassicTransport.kt`

ClassicTransport is Android API — tested manually on device, not in unit tests.

- [ ] **Step 1: Create ClassicTransport.kt**

```kotlin
package com.frodrigues.jaecoo.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ClassicTransport(private val device: BluetoothDevice) : BluetoothTransport {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        s.connect()
        socket = s
        inputStream = s.inputStream
        outputStream = s.outputStream
        _isConnected.value = true
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        _isConnected.value = false
        runCatching { socket?.close() }
        socket = null
        inputStream = null
        outputStream = null
    }

    override suspend fun sendCommand(command: String): String = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IOException("Not connected")
        val inp = inputStream ?: throw IOException("Not connected")
        out.write("$command\r".toByteArray(Charsets.UTF_8))
        out.flush()
        readUntilPrompt(inp)
    }

    private fun readUntilPrompt(inp: InputStream): String {
        val buffer = StringBuilder()
        val b = ByteArray(1)
        while (true) {
            val read = inp.read(b)
            if (read == -1) throw IOException("Stream closed")
            val ch = b[0].toInt().toChar()
            if (ch == '>') break
            buffer.append(ch)
        }
        return buffer.toString().trim()
    }

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
```

- [ ] **Step 2: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/bluetooth/ClassicTransport.kt
git commit -m "feat: add ClassicTransport SPP Bluetooth implementation"
```

---

## Task 11: BleTransport (GATT Bluetooth)

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/bluetooth/BleTransport.kt`

BleTransport uses Android GATT API (minSdk 33 — API 33 uses the new `onCharacteristicChanged(gatt, char, value: ByteArray)` callback). Tested manually on device.

- [ ] **Step 1: Create BleTransport.kt**

```kotlin
package com.frodrigues.jaecoo.bluetooth

import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID

class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
    serviceUuid: String = "0000fff0-0000-1000-8000-00805f9b34fb"
) : BluetoothTransport {

    private val serviceUUID = UUID.fromString(serviceUuid)
    private val writeUUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    private val notifyUUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    private val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val responseChannel = Channel<String>(Channel.BUFFERED)
    private val responseBuffer = StringBuilder()
    private val connectLatch = CompletableDeferred<Unit>()

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else {
                _isConnected.value = false
                connectLatch.completeExceptionally(IOException("BLE disconnected, status=$status"))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connectLatch.completeExceptionally(IOException("Service discovery failed: $status"))
                return
            }
            val service = gatt.getService(serviceUUID)
            val notifyChar = service?.getCharacteristic(notifyUUID)
            if (notifyChar == null) {
                connectLatch.completeExceptionally(IOException("Notify characteristic not found"))
                return
            }
            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(cccdUUID)
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
            _isConnected.value = true
            connectLatch.complete(Unit)
        }

        // Android 13+ (API 33) callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val chunk = value.toString(Charsets.UTF_8)
            responseBuffer.append(chunk)
            if (responseBuffer.contains('>')) {
                val response = responseBuffer.toString().substringBefore('>').trim()
                responseBuffer.clear()
                responseChannel.trySend(response)
            }
        }
    }

    override suspend fun connect() {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        withTimeout(10_000) { connectLatch.await() }
    }

    override suspend fun disconnect() {
        _isConnected.value = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    override suspend fun sendCommand(command: String): String {
        val g = gatt ?: throw IOException("Not connected")
        val service = g.getService(serviceUUID) ?: throw IOException("Service not found")
        val writeChar = service.getCharacteristic(writeUUID) ?: throw IOException("Write char not found")
        writeChar.value = "$command\r".toByteArray(Charsets.UTF_8)
        g.writeCharacteristic(writeChar)
        return withTimeout(5_000) { responseChannel.receive() }
    }
}
```

- [ ] **Step 2: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/bluetooth/BleTransport.kt
git commit -m "feat: add BleTransport GATT implementation for ELM327 BLE adapters"
```

---

## Task 12: BluetoothTransportFactory

**Files:**
- Modify: `app/src/main/java/com/frodrigues/jaecoo/bluetooth/BluetoothTransport.kt`

Add the factory to the same file as the interface.

- [ ] **Step 1: Add BluetoothTransportFactory to BluetoothTransport.kt**

Replace the file contents:

```kotlin
package com.frodrigues.jaecoo.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface BluetoothTransport {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendCommand(command: String): String
    val isConnected: StateFlow<Boolean>
}

object BluetoothTransportFactory {
    fun create(device: BluetoothDevice, context: Context): BluetoothTransport {
        return when (device.type) {
            BluetoothDevice.DEVICE_TYPE_LE -> BleTransport(context, device)
            BluetoothDevice.DEVICE_TYPE_DUAL -> ClassicTransport(device)
            else -> ClassicTransport(device) // DEVICE_TYPE_CLASSIC or DEVICE_TYPE_UNKNOWN
        }
    }
}
```

- [ ] **Step 2: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/bluetooth/BluetoothTransport.kt
git commit -m "feat: add BluetoothTransportFactory with Classic/BLE detection"
```

---

## Task 13: MqttPublisher + HaDiscoveryPublisher + Tests

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/mqtt/MqttPublisher.kt`
- Create: `app/src/main/java/com/frodrigues/jaecoo/mqtt/HaDiscoveryPublisher.kt`
- Create: `app/src/test/java/com/frodrigues/jaecoo/mqtt/HaDiscoveryPublisherTest.kt`

- [ ] **Step 1: Create MqttPublisher.kt**

```kotlin
package com.frodrigues.jaecoo.mqtt

import com.frodrigues.jaecoo.settings.AppConfig
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MqttPublisher(private val config: AppConfig) {

    private lateinit var client: Mqtt3AsyncClient

    suspend fun connect() = withContext(Dispatchers.IO) {
        client = MqttClient.builder()
            .useMqttVersion3()
            .identifier(config.mqttClientId)
            .serverHost(config.mqttHost)
            .serverPort(config.mqttPort)
            .buildAsync()

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
```

- [ ] **Step 2: Write failing test for HaDiscoveryPublisher**

Create `app/src/test/java/com/frodrigues/jaecoo/mqtt/HaDiscoveryPublisherTest.kt`:

```kotlin
package com.frodrigues.jaecoo.mqtt

import com.frodrigues.jaecoo.settings.AppConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HaDiscoveryPublisherTest {

    private val config = AppConfig(
        btDeviceMac = "AA:BB:CC:DD:EE:FF",
        mqttHost = "192.168.1.10",
        mqttPort = 1883,
        mqttUser = "",
        mqttPassword = "",
        pollIntervalSeconds = 5,
        deviceName = "Test Car",
        deviceModel = "Model X",
        deviceManufacturer = "Maker"
    )

    @Test
    fun `publishes discovery config for RPM with correct topic`() = runTest {
        val published = mutableListOf<Pair<String, String>>()
        val publisher = HaDiscoveryPublisher(
            publish = { topic, payload -> published.add(topic to payload) },
            config = config
        )

        publisher.publishDiscovery(setOf(0x0C), "AABBCCDDEEFF")

        assertEquals(1, published.size)
        assertEquals(
            "homeassistant/sensor/obd2_AABBCCDDEEFF/0C/config",
            published[0].first
        )
    }

    @Test
    fun `discovery payload contains required HA fields`() = runTest {
        val published = mutableListOf<Pair<String, String>>()
        val publisher = HaDiscoveryPublisher(
            publish = { topic, payload -> published.add(topic to payload) },
            config = config
        )

        publisher.publishDiscovery(setOf(0x0C), "AABBCCDDEEFF")

        val payload = published[0].second
        assertTrue(payload.contains("\"state_topic\""))
        assertTrue(payload.contains("obd2/AABBCCDDEEFF/0C/state"))
        assertTrue(payload.contains("\"unique_id\""))
        assertTrue(payload.contains("\"unit_of_measurement\""))
        assertTrue(payload.contains("Test Car RPM"))
    }

    @Test
    fun `discovery payload includes device block with config values`() = runTest {
        val published = mutableListOf<Pair<String, String>>()
        val publisher = HaDiscoveryPublisher(
            publish = { topic, payload -> published.add(topic to payload) },
            config = config
        )

        publisher.publishDiscovery(setOf(0x0C), "AABBCCDDEEFF")

        val payload = published[0].second
        assertTrue(payload.contains("\"name\":\"Test Car\""))
        assertTrue(payload.contains("\"model\":\"Model X\""))
        assertTrue(payload.contains("\"manufacturer\":\"Maker\""))
    }

    @Test
    fun `skips unknown pids`() = runTest {
        val published = mutableListOf<Pair<String, String>>()
        val publisher = HaDiscoveryPublisher(
            publish = { topic, payload -> published.add(topic to payload) },
            config = config
        )

        publisher.publishDiscovery(setOf(0xFF), "AABBCCDDEEFF")

        assertTrue(published.isEmpty())
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.mqtt.HaDiscoveryPublisherTest"
```

Expected: FAILED — `HaDiscoveryPublisher` does not exist yet

- [ ] **Step 4: Create HaDiscoveryPublisher.kt**

```kotlin
package com.frodrigues.jaecoo.mqtt

import com.frodrigues.jaecoo.obd.PidDefinition
import com.frodrigues.jaecoo.obd.PidRegistry
import com.frodrigues.jaecoo.settings.AppConfig
import org.json.JSONArray
import org.json.JSONObject

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

    private fun buildPayload(def: PidDefinition, mac: String, pidHex: String): String {
        return JSONObject().apply {
            put("name", "${config.deviceName} ${def.name}")
            put("state_topic", "obd2/$mac/$pidHex/state")
            put("unit_of_measurement", def.unit)
            put("unique_id", "${mac}_$pidHex")
            def.haDeviceClass?.let { put("device_class", it) }
            put("device", JSONObject().apply {
                put("identifiers", JSONArray().put("obd2_$mac"))
                put("name", config.deviceName)
                put("model", config.deviceModel)
                put("manufacturer", config.deviceManufacturer)
            })
        }.toString()
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```
.\gradlew testDebugUnitTest --tests "com.frodrigues.jaecoo.mqtt.HaDiscoveryPublisherTest"
```

Expected: 4 tests PASSED

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/mqtt/MqttPublisher.kt app/src/main/java/com/frodrigues/jaecoo/mqtt/HaDiscoveryPublisher.kt app/src/test/java/com/frodrigues/jaecoo/mqtt/HaDiscoveryPublisherTest.kt
git commit -m "feat: add MqttPublisher and HaDiscoveryPublisher with HA MQTT auto-discovery"
```

---

## Task 14: OBDCollectorService (ForegroundService)

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/service/OBDCollectorService.kt`

- [ ] **Step 1: Create OBDCollectorService.kt**

```kotlin
package com.frodrigues.jaecoo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.frodrigues.jaecoo.MainActivity
import com.frodrigues.jaecoo.bluetooth.BluetoothTransportFactory
import com.frodrigues.jaecoo.mqtt.HaDiscoveryPublisher
import com.frodrigues.jaecoo.mqtt.MqttPublisher
import com.frodrigues.jaecoo.obd.ObdCommandExecutor
import com.frodrigues.jaecoo.obd.PidPoller
import com.frodrigues.jaecoo.obd.PidScanner
import com.frodrigues.jaecoo.settings.AppSettings
import com.frodrigues.jaecoo.settings.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServiceStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING, ERROR }

class OBDCollectorService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun getService(): OBDCollectorService = this@OBDCollectorService
    }

    private val binder = LocalBinder()

    val status: MutableStateFlow<ServiceStatus> = MutableStateFlow(ServiceStatus.IDLE)
    val activePidCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val lastUpdateTime: MutableStateFlow<Long> = MutableStateFlow(0L)

    private lateinit var settings: AppSettings
    private var collectorJob: Job? = null
    private var currentStateTopics: List<String> = emptyList()
    private var mqttPublisherRef: MqttPublisher? = null

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(applicationContext.dataStore)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startCollection()
            ACTION_STOP -> stopCollection()
        }
        return START_STICKY
    }

    private fun startCollection() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Starting..."),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        collectorJob?.cancel()
        collectorJob = lifecycleScope.launch { collectWithRetry() }
    }

    private suspend fun collectWithRetry() {
        val backoffMs = listOf(5_000L, 10_000L, 30_000L, 60_000L)
        var attempt = 0

        while (true) {
            try {
                collect()
                attempt = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.value = ServiceStatus.RECONNECTING
                val delay = backoffMs.getOrElse(attempt) { 60_000L }
                attempt = minOf(attempt + 1, backoffMs.lastIndex)
                updateNotification("Reconnecting in ${delay / 1000}s... (${e.message})")
                delay(delay)
            }
        }
    }

    private suspend fun collect() {
        val config = settings.snapshot()
        if (config.btDeviceMac.isBlank()) throw IllegalStateException("No BT device configured")
        if (config.mqttHost.isBlank()) throw IllegalStateException("No MQTT host configured")

        status.value = ServiceStatus.CONNECTING
        updateNotification("Connecting to Bluetooth...")

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("Bluetooth not available")
        val device = bluetoothAdapter.getRemoteDevice(config.btDeviceMac)
        val transport = BluetoothTransportFactory.create(device, applicationContext)
        transport.connect()

        try {
            updateNotification("Initializing ELM327...")
            val executor = ObdCommandExecutor(transport)
            executor.initialize()

            updateNotification("Scanning supported PIDs...")
            val supportedPids = PidScanner(executor).scan()
            activePidCount.value = supportedPids.size

            updateNotification("Connecting to MQTT...")
            val mqttPublisher = MqttPublisher(config)
            mqttPublisher.connect()

            val mac = device.address.replace(":", "")
            currentStateTopics = supportedPids.map { pid ->
                "obd2/$mac/${pid.toString(16).padStart(2, '0').uppercase()}/state"
            }
            mqttPublisherRef = mqttPublisher

            HaDiscoveryPublisher(
                publish = { t, p -> mqttPublisher.publish(t, p, retain = true) },
                config = config
            ).publishDiscovery(supportedPids, mac)

            status.value = ServiceStatus.CONNECTED
            updateNotification("Connected — ${supportedPids.size} PIDs")

            PidPoller(executor, supportedPids, config.pollIntervalSeconds).readings().collect { reading ->
                val pidHex = reading.pid.toString(16).padStart(2, '0').uppercase()
                val value = reading.value.toBigDecimal().stripTrailingZeros().toPlainString()
                mqttPublisher.publish("obd2/$mac/$pidHex/state", value, retain = true)
                lastUpdateTime.value = reading.timestamp
            }
        } finally {
            publishUnavailable()
            transport.disconnect()
        }
    }

    private fun stopCollection() {
        collectorJob?.cancel()
        collectorJob = null
        status.value = ServiceStatus.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun publishUnavailable() {
        val mqtt = mqttPublisherRef ?: return
        currentStateTopics.forEach { topic ->
            runCatching { mqtt.publishEmpty(topic) }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OBD2 Collector",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OBD2 Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.frodrigues.jaecoo.START"
        const val ACTION_STOP = "com.frodrigues.jaecoo.STOP"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "obd_collector"
    }
}
```

- [ ] **Step 2: Verify build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/service/OBDCollectorService.kt
git commit -m "feat: add OBDCollectorService ForegroundService with retry backoff"
```

---

## Task 15: ViewModel + UI Screens + MainActivity Wiring

**Files:**
- Create: `app/src/main/java/com/frodrigues/jaecoo/ui/MainViewModel.kt`
- Create: `app/src/main/java/com/frodrigues/jaecoo/ui/MainScreen.kt`
- Create: `app/src/main/java/com/frodrigues/jaecoo/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/frodrigues/jaecoo/MainActivity.kt`

- [ ] **Step 1: Create MainViewModel.kt**

```kotlin
package com.frodrigues.jaecoo.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.frodrigues.jaecoo.service.OBDCollectorService
import com.frodrigues.jaecoo.service.ServiceStatus
import com.frodrigues.jaecoo.settings.AppSettings
import com.frodrigues.jaecoo.settings.dataStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settings = AppSettings(app.dataStore)

    val serviceStatus: MutableStateFlow<ServiceStatus> = MutableStateFlow(ServiceStatus.IDLE)
    val activePidCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val lastUpdateTime: MutableStateFlow<Long> = MutableStateFlow(0L)

    fun startService() {
        val intent = Intent(getApplication(), OBDCollectorService::class.java).apply {
            action = OBDCollectorService.ACTION_START
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopService() {
        val intent = Intent(getApplication(), OBDCollectorService::class.java).apply {
            action = OBDCollectorService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }
}
```

- [ ] **Step 2: Create MainScreen.kt**

```kotlin
package com.frodrigues.jaecoo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frodrigues.jaecoo.service.ServiceStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val status by viewModel.serviceStatus.collectAsState()
    val pidCount by viewModel.activePidCount.collectAsState()
    val lastUpdate by viewModel.lastUpdateTime.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OBD2 Bridge") },
                actions = {
                    TextButton(onClick = onNavigateToSettings) { Text("Settings") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status: ${status.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Active PIDs: $pidCount")
                    if (lastUpdate > 0L) {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(lastUpdate))
                        Text("Last update: $time")
                    }
                }
            }

            val isRunning = status == ServiceStatus.CONNECTED || status == ServiceStatus.CONNECTING ||
                            status == ServiceStatus.RECONNECTING
            Button(
                onClick = { if (isRunning) viewModel.stopService() else viewModel.startService() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Stop" else "Start")
            }
        }
    }
}
```

- [ ] **Step 3: Create SettingsScreen.kt**

```kotlin
package com.frodrigues.jaecoo.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.frodrigues.jaecoo.settings.AppSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val btMac by settings.btDeviceMac.collectAsState(initial = "")
    val mqttHost by settings.mqttHost.collectAsState(initial = "")
    val mqttPort by settings.mqttPort.collectAsState(initial = 1883)
    val mqttUser by settings.mqttUser.collectAsState(initial = "")
    val mqttPassword by settings.mqttPassword.collectAsState(initial = "")
    val pollInterval by settings.pollIntervalSeconds.collectAsState(initial = 5)
    val deviceName by settings.deviceName.collectAsState(initial = "Jaecoo 7")
    val deviceModel by settings.deviceModel.collectAsState(initial = "Jaecoo 7")
    val deviceManufacturer by settings.deviceManufacturer.collectAsState(initial = "Jaecoo")

    val pairedDevices: List<BluetoothDevice> = remember {
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Bluetooth Device", style = MaterialTheme.typography.titleSmall)
            pairedDevices.forEach { device ->
                val name = runCatching { device.name }.getOrDefault(device.address)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            settings.update {
                                this[AppSettings.BT_DEVICE_MAC] = device.address
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (device.address == btMac) "✓ $name" else name)
                }
            }

            HorizontalDivider()
            Text("MQTT", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = mqttHost,
                onValueChange = { v ->
                    scope.launch { settings.update { this[AppSettings.MQTT_HOST] = v } }
                },
                label = { Text("Host") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mqttPort.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { port ->
                        scope.launch { settings.update { this[AppSettings.MQTT_PORT] = port } }
                    }
                },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mqttUser,
                onValueChange = { v ->
                    scope.launch { settings.update { this[AppSettings.MQTT_USER] = v } }
                },
                label = { Text("Username (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mqttPassword,
                onValueChange = { v ->
                    scope.launch { settings.update { this[AppSettings.MQTT_PASSWORD] = v } }
                },
                label = { Text("Password (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Poll Interval: ${pollInterval}s", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = pollInterval.toFloat(),
                onValueChange = { v ->
                    scope.launch {
                        settings.update { this[AppSettings.POLL_INTERVAL_SECONDS] = v.toInt() }
                    }
                },
                valueRange = 1f..60f,
                steps = 58,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Device Info", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = deviceName,
                onValueChange = { v ->
                    scope.launch { settings.update { this[AppSettings.DEVICE_NAME] = v } }
                },
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deviceModel,
                onValueChange = { v ->
                    scope.launch { settings.update { this[AppSettings.DEVICE_MODEL] = v } }
                },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deviceManufacturer,
                onValueChange = { v ->
                    scope.launch { settings.update { this[AppSettings.DEVICE_MANUFACTURER] = v } }
                },
                label = { Text("Manufacturer") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

- [ ] **Step 4: Update MainActivity.kt**

Replace contents:

```kotlin
package com.frodrigues.jaecoo

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.frodrigues.jaecoo.ui.MainScreen
import com.frodrigues.jaecoo.ui.MainViewModel
import com.frodrigues.jaecoo.ui.SettingsScreen
import com.frodrigues.jaecoo.ui.theme.JaecooTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — service will handle missing permissions gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        setContent {
            JaecooTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = viewModel.settings,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

- [ ] **Step 5: Verify full build**

```
.\gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run all unit tests**

```
.\gradlew testDebugUnitTest
```

Expected: All tests PASSED

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/frodrigues/jaecoo/ui/MainViewModel.kt app/src/main/java/com/frodrigues/jaecoo/ui/MainScreen.kt app/src/main/java/com/frodrigues/jaecoo/ui/SettingsScreen.kt app/src/main/java/com/frodrigues/jaecoo/MainActivity.kt
git commit -m "feat: add main UI, settings screen, and permission request flow"
```

---

## Post-Implementation Manual Tests (on device with ELM327 + car)

Before declaring done, verify on physical hardware:

1. **BT pairing:** Pair ELM327 adapter in Android Settings → Bluetooth
2. **App settings:** Open app → Settings → select paired ELM327 device → enter MQTT broker IP → tap back
3. **Start collection:** Tap Start on main screen — verify notification appears
4. **HA check:** Open Home Assistant → verify new device "Jaecoo 7" appears under MQTT devices with all supported sensors
5. **Live data:** Verify sensor values update in HA dashboard — RPM changes with engine rev, speed changes while driving
6. **Stop + unavailable:** Tap Stop → verify HA sensors go unavailable within one poll cycle
7. **BLE adapter:** If testing BLE ELM327, verify connection works; if service UUID differs from `0xFFF0`, update via `BleTransport` constructor parameter (hardcode in factory for now)
