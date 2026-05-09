# OBD2 → Home Assistant Bridge — Design Spec

**Date:** 2026-05-09  
**Project:** Jaecoo Android App (`com.frodrigues.jaecoo`)  
**Goal:** Android background service that reads OBD2 data via ELM327 Bluetooth and publishes to Home Assistant via MQTT.

---

## 1. Architecture Overview

```
ELM327 ──BT──► BluetoothTransport
                      │
                      ▼
              ObdCommandExecutor
                      │
               PidScanner (boot)
               PidPoller  (loop)
                      │
                      ▼
             MqttPublisher ──► Home Assistant
                      ▲
              OBDCollectorService (ForegroundService orchestrates)
```

### Layers

| Layer | Classes | Responsibility |
|---|---|---|
| **BT Abstraction** | `BluetoothTransport` (interface), `ClassicTransport`, `BleTransport`, `BluetoothTransportFactory` | Send/receive AT strings, hide Classic vs BLE difference |
| **OBD2 Protocol** | `ObdCommandExecutor`, `PidScanner`, `PidPoller`, `PidParser` | Init ELM327, discover supported PIDs, poll loop, parse responses |
| **MQTT** | `MqttPublisher`, `HaDiscoveryPublisher` | Publish HA discovery config + sensor states |
| **Service** | `OBDCollectorService` | ForegroundService orchestrates lifecycle |
| **Settings** | `AppSettings` (DataStore) | Persist all user configuration |
| **UI** | `MainActivity`, `SettingsScreen` | Status display + configuration |

---

## 2. OBD2 Protocol Layer

### ELM327 Init Sequence
```
ATZ       reset
ATE0      echo off
ATL0      linefeeds off
ATH1      headers on (required to parse CAN responses)
ATSP6     ISO 15765-4 CAN 11-bit 500kbaud (confirmed on Jaecoo 7)
ATAT1     adaptive timing on (ELM327 auto-adjusts timeout per response; default ~200ms per command)
```

### PID Discovery (Mode 01 Scan)
Send support bitmask requests in sequence:
```
0100 → supported PIDs 01–1F
0120 → supported PIDs 21–3F
0140 → supported PIDs 41–5F
0160 → supported PIDs 61–7F
0180 → supported PIDs 81–9F
01A0 → supported PIDs A1–BF
01C0 → supported PIDs C1–DF
```
Each response is 4 bytes (32 bits). `PidScanner` builds `Set<Int>` of supported PIDs. Only supported PIDs are polled.

### Polling Loop
`PidPoller` round-robins through `Set<Int>`. Configurable interval (default 5s per full cycle). `PidParser` converts raw hex response to typed value using formula for each PID.

### PID Mapping

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
| 0x31 | Distance since cleared | km | 256A+B | `distance` |
| 0x33 | Barometric Pressure | kPa | A | `pressure` |
| 0x42 | Control Module Voltage | V | (256A+B)/1000 | `voltage` |
| 0x43 | Absolute Load | % | 100×(256A+B)/255 | — |
| 0x45 | Relative Throttle | % | 100A/255 | — |
| 0x46 | Ambient Air Temp | °C | A−40 | `temperature` |
| 0x49 | Accel Pedal D | % | 100A/255 | — |
| 0x4A | Accel Pedal E | % | 100A/255 | — |
| 0x4C | Commanded Throttle | % | 100A/255 | — |
| 0x4D | Time w/ MIL | min | 256A+B | `duration` |
| 0x4E | Time since cleared | min | 256A+B | `duration` |
| 0x52 | Ethanol % | % | 100A/255 | — |
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

PIDs not in the table above: published as generic sensor with raw hex string value.

---

## 3. Bluetooth Abstraction Layer

### Interface
```kotlin
interface BluetoothTransport {
    suspend fun connect(device: BluetoothDevice)
    suspend fun disconnect()
    suspend fun sendCommand(command: String): String
    val isConnected: StateFlow<Boolean>
}
```

### ClassicTransport (SPP)
- UUID: `00001101-0000-1000-8000-00805F9B34FB`
- `BluetoothSocket` → `InputStream`/`OutputStream`
- Reads until `>` prompt from ELM327

### BleTransport
- Scans for device by MAC address
- Service UUID: `0xFFF0` (common ELM327 BLE default; may vary by adapter — configurable in settings)
- Write characteristic: `0xFFF1`
- Notify characteristic: `0xFFF2`
- Accumulates GATT notifications until `>` received

### BluetoothTransportFactory
- Detects transport type via `BluetoothDevice.type`
- `DEVICE_TYPE_CLASSIC` → `ClassicTransport`
- `DEVICE_TYPE_LE` → `BleTransport`
- `DEVICE_TYPE_DUAL` → try Classic first, fallback to BLE

### Android Permissions (minSdk 33)
```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

---

## 4. MQTT + HA Discovery

### Library
HiveMQ MQTT Client (`com.hivemq:hivemq-mqtt-client`) — coroutines-friendly, no deprecated dependencies.

### HA MQTT Discovery
Published once per PID at service start (and after MQTT reconnect):

```
Topic:   homeassistant/sensor/obd2_<mac>/<pid_name>/config
Payload:
{
  "name": "<deviceName> RPM",
  "state_topic": "obd2/<mac>/0C/state",
  "unit_of_measurement": "rpm",
  "unique_id": "<mac>_0C",
  "device": {
    "identifiers": ["obd2_<mac>"],
    "name": "<deviceName>",
    "model": "<deviceModel>",
    "manufacturer": "<deviceManufacturer>"
  }
}
```

### State Publishing
Each poll cycle publishes per-PID:
```
Topic:   obd2/<mac>/0C/state
Payload: "2450"    ← formula-converted value as string
```

### MQTT Config (DataStore)
| Key | Default |
|---|---|
| `mqttHost` | — |
| `mqttPort` | 1883 |
| `mqttUser` | — |
| `mqttPassword` | — |
| `mqttClientId` | `obd2_android_<mac>` |
| `mqttRetain` | true |
| `mqttQos` | 1 |

### Unavailable State
When car shuts off (BT disconnects or CAN timeout): publish empty payload to all state topics → HA marks sensors `unavailable`.

---

## 5. ForegroundService + Settings UI

### OBDCollectorService Lifecycle
```
START
  → connect Bluetooth
  → init ELM327 (AT commands)
  → scan supported PIDs
  → connect MQTT
  → publish HA Discovery configs
  → start poll loop
      → for each supported PID: sendCommand → parse → publish MQTT
      → wait interval
      → repeat
STOP
  → publish unavailable (empty payloads)
  → disconnect Bluetooth
  → disconnect MQTT
  → remove notification
```

Errors → exponential backoff retry: 5s, 10s, 30s, 60s.

### Persistent Notification
- Status: `Connected | Disconnected | Reconnecting...`
- Active PIDs count
- Last update timestamp

### AppSettings (DataStore keys)

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

### UI (Jetpack Compose)

**MainActivity:**
- Connection status card
- Active PID count
- Last update time
- Start / Stop button

**SettingsScreen:**
- BT device picker (list of paired devices)
- MQTT: host, port, user, password
- Poll interval slider (1–60s)
- Device name, model, manufacturer fields

---

## 6. Error Handling

| Scenario | Behavior |
|---|---|
| BT disconnects | Retry backoff: 5s → 10s → 30s → 60s |
| ELM327 `NO DATA` / `ERROR` | Skip PID, retry next cycle |
| MQTT offline | HiveMQ auto-reconnect; queue locally until reconnected |
| Car shuts off (CAN timeout) | Publish empty payloads → HA sensors go `unavailable` |
| Invalid PID response | Log, discard, do not publish |

---

## 7. Testing

| Layer | Strategy |
|---|---|
| `PidParser` | Unit tests: hex string → expected value per PID |
| `PidScanner` | Unit tests: bitmask bytes → correct PID set |
| `BluetoothTransport` | Interface is mockable; `FakeTransport` for integration tests |
| `MqttPublisher` | Mock MQTT client; verify correct topics and payloads |
| `OBDCollectorService` | Instrumented test with `FakeTransport` |

---

## 8. Dependencies to Add

```kotlin
// HiveMQ MQTT Client
implementation("com.hivemq:hivemq-mqtt-client:1.3.3")

// DataStore Preferences
implementation("androidx.datastore:datastore-preferences:1.1.1")

// Lifecycle Service
implementation("androidx.lifecycle:lifecycle-service:2.8.7")
```
