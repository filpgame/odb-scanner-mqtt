package com.frodrigues.jaecoo.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.frodrigues.jaecoo.settings.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var pairedDevices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                pairedDevices = try {
                    @Suppress("DEPRECATION")
                    BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            if (pairedDevices.isEmpty()) {
                Text("No paired devices found. Pair your ELM327 in Android Settings first.")
            }
            pairedDevices.forEach { device ->
                val name = runCatching { device.name }.getOrDefault(device.address)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            settings.update { this[AppSettings.BT_DEVICE_MAC] = device.address }
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
