# OBD2 → Home Assistant Bridge — Design Spec

**Date:** 2026-05-09 (last updated: 2026-05-10)
**Project:** Android App (`com.frodrigues.odbmqtt`)
**Repo:** https://github.com/filpgame/odb-scanner-mqtt
**Goal:** Android background service that reads OBD2 data via ELM327 Bluetooth and publishes to Home Assistant via MQTT Discovery.

---

## 1. Architecture Overview

```
ELM327 ──BT──► BluetoothTransport (Classic/BLE)
                      │
                      ▼
              ObdCommandExecutor (AT init)
                      │
               PidScanner (bitmask scan)
               PidPoller  (round-robin loop)
                      │
                      ▼
             MqttPublisher ──► Home Assistant
                      ▲
              OBDCollectorService (ForegroundService)
```

### Layers

| Layer | Classes | Responsibility |
|---|---|---|
| **BT Abstraction** | `BluetoothTransport` (interface), `ClassicTransport`, `BleTransport`, `BluetoothTransportFactory` | Send/receive AT strings, hide Classic vs BLE difference |
| **OBD2 Protocol** | `ObdCommandExecutor`, `PidScanner`, `PidPoller`, `PidParser` | Init ELM327, discover supported PIDs, poll loop, parse responses |
| **MQTT** | `MqttPublisher`, `HaDiscoveryPublisher`, `testMqttConnection()` | Publish HA discovery config + sensor states; test connectivity |
| **Service** | `OBDCollectorService` | ForegroundService orchestrator with exponential backoff |
| **Settings** | `AppSettings` (DataStore), `AppConfig` | Persist all user configuration |
| **UI** | `MainActivity`, `MainScreen`, `SettingsScreen`, `MainViewModel` | Status display, PID list, configuration |

---

## 2. OBD2 Protocol Layer

### ELM327 Init Sequence
```
ATZ   → reset (validates "ELM" in response; throws if not ELM327)
ATE0  → echo off
ATL0  → linefeeds off
ATH1  → headers on (required to parse CAN responses)
ATSP6 → ISO 15765-4 CAN 11-bit 500kbaud (confirmed on Jaecoo 7)
ATAT1 → adaptive timing (~200ms default per command)
```

### PID Discovery (Mode 01 bitmask scan)
```
0100 → PIDs 01–1F  |  0120 → PIDs 21–3F  |  0140 → PIDs 41–5F
0160 → PIDs 61–7F  |  0180 → PIDs 81–9F  |  01A0 → PIDs A1–BF  |  01C0 → PIDs C1–DF
```
Each response is 4 bytes (32 bits). `PidScanner` builds `Set<Int>`. Stops when response is null or last bit is 0. Support PIDs (multiples of 0x20) excluded from result.

### Polling Loop
`PidPoller` round-robins through `Set<Int>`, emits `Flow<PidReading>`. Fixed interval options: **1 / 5 / 10 / 30 / 60 seconds** (default 5s).

`PidReading(pid: Int, value: Double, timestamp: Long)`

### PID Mapping (39 PIDs)

| PID | Name | Unit | Formula | HA device_class |
|---|---|---|---|---|
| 0x04 | Engine Load | % | A/2.55 | — |
| 0x05 | Coolant Temp | °C | A−40 | `temperature` |
| 0x06 | Short Fuel Trim B1 | % | A/1.28−100 | — |
| 0x07 | Long Fuel Trim B1 | % | A/1.28−100 | — |
| 0x0A | Fuel Pressure | kPa | 3×A | `pressure` |
| 0x0B | Intake MAP | kPa | A | `pressure` |
| 0x0C | RPM | rpm | (256A+B)/4 | — |
| 0x0D | Speed | km/h | A | `speed` |
| 0x0E | Timing Advance | ° | A/2−64 | — |
| 0x0F | Intake Air Temp | °C | A−40 | `temperature` |
| 0x10 | MAF Rate | g/s | (256A+B)/100 | — |
| 0x11 | Throttle Position | % | 100A/255 | — |
| 0x1F | Run Time | s | 256A+B | `duration` |
| 0x21 | Distance w/ MIL | km | 256A+B | `distance` |
| 0x22 | Fuel Rail Pressure | kPa | 0.079×(256A+B) | `pressure` |
| 0x2C | Commanded EGR | % | 100A/255 | — |
| 0x2F | Fuel Level | % | 100A/255 | — |
| 0x31 | Distance Since Cleared | km | 256A+B | `distance` |
| 0x33 | Barometric Pressure | kPa | A | `pressure` |
| 0x42 | Control Module Voltage | V | (256A+B)/1000 | `voltage` |
| 0x43 | Absolute Load | % | 100×(256A+B)/255 | — |
| 0x45 | Relative Throttle | % | 100A/255 | — |
| 0x46 | Ambient Air Temp | °C | A−40 | `temperature` |
| 0x49 | Accel Pedal D | % | 100A/255 | — |
| 0x4A | Accel Pedal E | % | 100A/255 | — |
| 0x4C | Commanded Throttle | % | 100A/255 | — |
| 0x4D | Time w/ MIL | min | 256A+B | `duration` |
| 0x4E | Time Since Cleared | min | 256A+B | `duration` |
| 0x52 | Ethanol Percent | % | 100A/255 | — |
| 0x5A | Relative Accel Pedal | % | 100A/255 | — |
| 0x5B | Hybrid Battery | % | 100A/255 | `battery` |
| 0x5C | Oil Temp | °C | A−40 | `temperature` |
| 0x5D | Injection Timing | ° | (256A+B)/128−210 | — |
| 0x5E | Fuel Rate | L/h | (256A+B)/20 | — |
| 0x61 | Driver Demand Torque | % | A−125 | — |
| 0x62 | Actual Torque | % | A−125 | — |
| 0x63 | Reference Torque | N·m | 256A+B | — |
| 0xA4 | Transmission Gear | ratio | (256C+D)/1000 | — |
| 0xA6 | Odometer | km | (A×2²⁴+B×2¹⁶+C×2⁸+D)/10 | `distance` |

PIDs not in the table: published as generic sensor with raw hex value.

---

## 3. Bluetooth Abstraction Layer

```kotlin
interface BluetoothTransport {
    suspend fun connect()           // device set at construction; no BluetoothDevice arg
    suspend fun disconnect()
    suspend fun sendCommand(command: String): String
    val isConnected: StateFlow<Boolean>
}
```

**ClassicTransport(device: BluetoothDevice)**
- UUID: `00001101-0000-1000-8000-00805F9B34FB`
- `sendCommand` wrapped in `withTimeout(5_000)` to prevent hang on ELM327 non-response

**BleTransport(context, device, serviceUuid = "0000fff0...")**
- Service UUID: `0xFFF0` (configurable — may vary by adapter)
- Write char: `0xFFF1`, Notify char: `0xFFF2`
- `responseBuffer` cleared at start of each `connect()` to prevent stale callback pollution
- `connectLatch` is a `var CompletableDeferred<Unit>` — reinitialised per `connect()` call
- `writeCharacteristic` result checked; throws `IOException` on non-zero status
- `_isConnected` set only after `onDescriptorWrite` (CCCD write confirmed)
- API 33+ `onCharacteristicChanged(gatt, char, value: ByteArray)` used

**BluetoothTransportFactory.create(device, context)**
- `DEVICE_TYPE_LE` → `BleTransport`; otherwise → `ClassicTransport`

**Android Permissions (minSdk 33)**
```xml
BLUETOOTH_CONNECT, BLUETOOTH_SCAN, FOREGROUND_SERVICE,
FOREGROUND_SERVICE_CONNECTED_DEVICE, POST_NOTIFICATIONS, INTERNET, ACCESS_NETWORK_STATE
```

---

## 4. MQTT + HA Discovery

**Library:** HiveMQ MQTT Client 1.3.x

**`MqttPublisher(config: AppConfig)`**
- `connect()`: creates `Mqtt3AsyncClient`, connects with optional auth; `client` only assigned on success
- `publish(topic, payload, retain=false)`: QoS AT_LEAST_ONCE
- `publishEmpty(topic)`: retain=true, QoS AT_LEAST_ONCE, no payload → HA marks sensor unavailable
- `disconnect()`: safe, nulls client

**`testMqttConnection(config: AppConfig): Result<Unit>`** (top-level suspend fun)
- Creates a test client, connects with 5s timeout, disconnects — used by Settings UI test button

**HA MQTT Discovery** — published once per PID at service start and after MQTT reconnect:
```
Topic:   homeassistant/sensor/obd2_<mac>/<PID>/config
Payload: { name, state_topic, unit_of_measurement, unique_id, device_class?, device{} }
```

**State topic:**
```
Topic:   obd2/<mac>/<PID>/state
Payload: formula-converted value as string (retained, QoS 1)
```

**AppSettings keys (DataStore):**

| Key | Type | Default |
|---|---|---|
| `btDeviceMac` | String | — |
| `mqttHost` | String | — |
| `mqttPort` | Int | 1883 |
| `mqttUser` | String | — |
| `mqttPassword` | String | — |
| `pollIntervalSeconds` | Int | 5 |
| `deviceName` | String | `Jaecoo 7` |
| `deviceModel` | String | `Jaecoo 7` |
| `deviceManufacturer` | String | `Jaecoo` |

`AppConfig.mqttClientId = "obd2_android_<mac_no_colons>"`

**Unavailable state:** on teardown, publish empty payload to all state topics.

---

## 5. OBDCollectorService (ForegroundService)

### Status Enums

```kotlin
enum class ServiceStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING }
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }
```

### Companion Object (shared state)

```kotlin
companion object {
    val status: MutableStateFlow<ServiceStatus>       // overall service state
    val activePidCount: MutableStateFlow<Int>
    val lastUpdateTime: MutableStateFlow<Long>
    val btStatus: MutableStateFlow<ConnectionStatus>  // BT/OBD2 connection
    val mqttStatus: MutableStateFlow<ConnectionStatus> // MQTT broker connection
    val pidReadings: MutableStateFlow<Map<Int, Double>> // live PID values
}
```

All companion flows reset in `onCreate()` and in `stopCollection()` via `invokeOnCompletion`.

### Lifecycle

```
START (ACTION_START or null for START_STICKY restart)
  → startForeground(FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
  → collectWithRetry() loop

collect():
  btStatus = CONNECTING
  → BT connect → ELM327 init → PID scan
  btStatus = CONNECTED
  mqttStatus = CONNECTING
  → MQTT connect
  mqttStatus = CONNECTED
  → publish HA Discovery
  → PidPoller loop:
      PidReading → pidReadings updated → MQTT publish
  finally:
      publishUnavailable()
      MQTT disconnect
      BT disconnect
      btStatus = DISCONNECTED
      mqttStatus = DISCONNECTED
      pidReadings = emptyMap()

STOP (ACTION_STOP)
  → job.cancel()
  → invokeOnCompletion: reset all companion flows
  → stopForeground + stopSelf
```

**Retry backoff:** 5s → 10s → 30s → 60s on any exception (re-throws `CancellationException`).

**Persistent notification:** shows current status text.

---

## 6. UI (Jetpack Compose + Material Design 3)

### Theme
`OdbMqttTheme` — dynamic color on API 31+ (`dynamicLightColorScheme`/`dynamicDarkColorScheme`); static fallback scheme.

### MainScreen

Two `ElevatedCard` rows:
1. **Connection status row** — two `ConnectionCard` side by side: **OBD2** and **MQTT Broker**
   - Colored dot + label: `primary` = Online, `tertiary` = Connecting, `onSurfaceVariant` = Offline
2. **Active PIDs card** — shows PID count, last update time, `FilledTonalButton "View PIDs"` (visible only when `pidCount > 0`)
3. **Start/Stop button** — fills width, uses `error`/`onError` colors when stopping

**PID List Dialog** (`AlertDialog`)
- Opens from "View PIDs" button
- Shows all entries from `pidReadings` sorted alphabetically by name
- Unknown PIDs shown as `PID 0xXX`; values formatted with unit
- Updates in real-time via `collectAsState` — no user action needed between updates
- Scrollable column with `heightIn(max = 480.dp)`

### SettingsScreen

Sections wrapped in `SettingsSection` (card with `surfaceContainerLow` background):

**Bluetooth Device**
- `OutlinedButton` showing selected device name (or "Tap to select…") — triggers `AlertDialog`
- Dialog: `ListItem + RadioButton` list, scrollable, refresh `IconButton` in title, empty state with "Open BT Settings" action
- Selected device MAC shown as `bodySmall` below button

**MQTT Broker**
- Host + Port in same `Row` (2:1 weight), with `supportingText`
- "Credentials (optional)" `labelMedium` subsection
- Username + Password fields
- `OutlinedButton "Test Connection"` with `CircularProgressIndicator` during test; inline result: `✓ Connected` or `✗ Failed: <message>`

**Polling**
- `SingleChoiceSegmentedButtonRow` with fixed options: **1s · 5s · 10s · 30s · 60s**

**Device (Home Assistant)**
- Explanation text: "These values appear in HA entity names and device info."
- Name + Model in same `Row`, Manufacturer below

**Cursor fix:** text fields use `rememberSaveable` local state initialised once via `settings.snapshot()` in `LaunchedEffect(Unit)`. DataStore re-emissions do not overwrite local state after initialisation.

---

## 7. Error Handling

| Scenario | Behavior |
|---|---|
| BT disconnects | Retry backoff: 5s → 10s → 30s → 60s |
| ELM327 response not "ELM" / "OK" | `IOException` thrown → retry |
| ELM327 `NO DATA` / `ERROR` | Skip PID (null parse result), retry next cycle |
| BLE write fails (non-zero result) | `IOException` → retry |
| MQTT broker offline | Retry backoff |
| Car shuts off | Publish empty payloads → HA sensors `unavailable` |
| `testMqttConnection` timeout | Returns `Result.failure(...)`, shown inline in Settings |

---

## 8. Versioning (CI/CD)

| Context | `versionCode` | `versionName` |
|---|---|---|
| Release tag `v1.2.3` | `GITHUB_RUN_NUMBER` | `1.2.3` |
| CI non-tag (PR / push) | `GITHUB_RUN_NUMBER` | `0.0.0-<sha7>` |
| Local build | `1` | `0.0.0-<gitSha7>` |

**GitHub Actions workflow** (`.github/workflows/release.yml`):
- Trigger: `push` to tag `v*`
- Builds debug + release APK
- Generates release notes from commits since previous tag
- Creates GitHub Release with APKs
- Requires `permissions: contents: write`

---

## 9. Dependencies

```kotlin
// Core
implementation("com.hivemq:hivemq-mqtt-client:1.3.x")
implementation("androidx.datastore:datastore-preferences:1.x")
implementation("androidx.lifecycle:lifecycle-service:2.x")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.x")
implementation("androidx.navigation:navigation-compose:2.x")

// UI
implementation(platform("androidx.compose:compose-bom:2026.05.00"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-core")

// Netty conflict fix (HiveMQ transitive)
packaging { resources { excludes += setOf(
    "META-INF/INDEX.LIST",
    "META-INF/io.netty.versions.properties"
) } }
```

---

## 10. Testing

| Layer | Strategy |
|---|---|
| `PidParser` | Unit: hex string → expected value per PID formula |
| `PidScanner` | Unit: bitmask bytes → correct PID set |
| `ObdResponseParser` | Unit: all null-return cases (NO DATA, ERROR, UNABLE, ?, blank) |
| `HaDiscoveryPublisher` | Unit: topic format, payload fields, unknown PID skip |
| `ObdCommandExecutor` | Unit: init sequence order via FakeTransport |
| `FakeTransport` | Test helper: `Map<String,String>` responses, `sentCommands` recorder |

---

## 11. Manual Verification Checklist (on device)

1. Pair ELM327 in Android Settings → Bluetooth
2. Open app → Settings → select device, enter MQTT host/port → **Test Connection** → `✓ Connected`
3. Tap **Start** → verify notification appears
4. OBD2 status dot → `Online`; MQTT Broker status dot → `Online`
5. PIDs count increases; tap **View PIDs** → list with values updating
6. HA: device "Jaecoo 7" auto-created under MQTT devices with all sensors
7. Tap **Stop** → HA sensors go `unavailable` within one poll cycle
