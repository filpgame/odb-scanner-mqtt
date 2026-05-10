package com.frodrigues.odbmqtt.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.frodrigues.odbmqtt.settings.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val btMac by settings.btDeviceMac.collectAsState(initial = "")
    val mqttHost by settings.mqttHost.collectAsState(initial = "")
    val mqttPort by settings.mqttPort.collectAsState(initial = 1883)
    val mqttUser by settings.mqttUser.collectAsState(initial = "")
    val mqttPassword by settings.mqttPassword.collectAsState(initial = "")
    val pollInterval by settings.pollIntervalSeconds.collectAsState(initial = 5)
    val deviceName by settings.deviceName.collectAsState(initial = "")
    val deviceModel by settings.deviceModel.collectAsState(initial = "")
    val deviceManufacturer by settings.deviceManufacturer.collectAsState(initial = "")

    var pairedDevices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }

    fun refreshDevices() {
        pairedDevices = try {
            context.getSystemService(BluetoothManager::class.java)
                ?.adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshDevices()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Bluetooth ─────────────────────────────────────────────────────
            SettingsSection(
                header = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("Bluetooth Device")
                        IconButton(
                            onClick = { refreshDevices() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh device list",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            ) {
                if (pairedDevices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "No paired devices found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)
                                )
                            }
                        ) {
                            Text("Open Bluetooth Settings")
                        }
                    }
                } else {
                    pairedDevices.forEachIndexed { index, device ->
                        val name = runCatching { device.name }.getOrDefault(device.address)
                        val selected = device.address == btMac

                        if (index > 0) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = selected,
                                    onClick = null
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.clickable(
                                role = Role.RadioButton,
                                onClickLabel = "Select $name"
                            ) {
                                scope.launch {
                                    settings.update { this[AppSettings.BT_DEVICE_MAC] = device.address }
                                }
                            }
                        )
                    }
                }
            }

            // ── MQTT Broker ───────────────────────────────────────────────────
            SettingsSection(header = { SectionLabel("MQTT Broker") }) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = mqttHost,
                            onValueChange = { v ->
                                scope.launch { settings.update { this[AppSettings.MQTT_HOST] = v } }
                            },
                            label = { Text("Host") },
                            supportingText = { Text("IP or hostname") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = mqttPort.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { port ->
                                    scope.launch { settings.update { this[AppSettings.MQTT_PORT] = port } }
                                }
                            },
                            label = { Text("Port") },
                            supportingText = { Text("Default: 1883") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "Credentials (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = mqttUser,
                        onValueChange = { v ->
                            scope.launch { settings.update { this[AppSettings.MQTT_USER] = v } }
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mqttPassword,
                        onValueChange = { v ->
                            scope.launch { settings.update { this[AppSettings.MQTT_PASSWORD] = v } }
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Polling ───────────────────────────────────────────────────────
            SettingsSection(header = { SectionLabel("Polling") }) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Update interval",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${pollInterval}s",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "60s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Device Info ───────────────────────────────────────────────────
            SettingsSection(header = { SectionLabel("Device (Home Assistant)") }) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "These values appear in HA entity names and device info.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { v ->
                                scope.launch { settings.update { this[AppSettings.DEVICE_NAME] = v } }
                            },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = deviceModel,
                            onValueChange = { v ->
                                scope.launch { settings.update { this[AppSettings.DEVICE_MODEL] = v } }
                            },
                            label = { Text("Model") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = deviceManufacturer,
                        onValueChange = { v ->
                            scope.launch { settings.update { this[AppSettings.DEVICE_MANUFACTURER] = v } }
                        },
                        label = { Text("Manufacturer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            header()
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
