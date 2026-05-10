package com.frodrigues.odbmqtt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frodrigues.odbmqtt.obd.PidRegistry
import com.frodrigues.odbmqtt.service.ConnectionStatus
import com.frodrigues.odbmqtt.service.ServiceStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val serviceStatus by viewModel.serviceStatus.collectAsState()
    val pidCount by viewModel.activePidCount.collectAsState()
    val lastUpdate by viewModel.lastUpdateTime.collectAsState()
    val btStatus by viewModel.btStatus.collectAsState()
    val mqttStatus by viewModel.mqttStatus.collectAsState()
    val pidReadings by viewModel.pidReadings.collectAsState()

    val isRunning = serviceStatus == ServiceStatus.CONNECTED ||
                    serviceStatus == ServiceStatus.CONNECTING ||
                    serviceStatus == ServiceStatus.RECONNECTING

    var showPidDialog by remember { mutableStateOf(false) }

    // PID list dialog
    if (showPidDialog) {
        AlertDialog(
            onDismissRequest = { showPidDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active PIDs")
                    Text(
                        text = "${pidReadings.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                if (pidReadings.isEmpty()) {
                    Text(
                        text = "No PID data yet. Start the service and wait for the first poll cycle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val currentReadings by viewModel.pidReadings.collectAsState()
                    val sortedEntries = currentReadings.entries
                        .sortedWith(compareBy {
                            PidRegistry.definitions[it.key]?.name ?: "zz_${it.key}"
                        })

                    Column(
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        sortedEntries.forEachIndexed { index, (pid, value) ->
                            val def = PidRegistry.definitions[pid]
                            val name = def?.name ?: "PID 0x${pid.toString(16).uppercase()}"
                            val unit = def?.unit ?: ""
                            val formatted = formatValue(value, unit)

                            if (index > 0) HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPidDialog = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "OBD2 Bridge",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Connection status cards ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionCard(
                    label = "OBD2",
                    status = btStatus,
                    modifier = Modifier.weight(1f)
                )
                ConnectionCard(
                    label = "MQTT Broker",
                    status = mqttStatus,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Active PIDs card ──────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Active PIDs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$pidCount",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (lastUpdate > 0L) {
                            Text(
                                text = "Updated ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdate))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (pidCount > 0) {
                        FilledTonalButton(onClick = { showPidDialog = true }) {
                            Text("View PIDs")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Start / Stop ──────────────────────────────────────────────────
            Button(
                onClick = { if (isRunning) viewModel.stopService() else viewModel.startService() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = if (isRunning)
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                else
                    ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = if (isRunning) "Stop" else "Start",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    label: String,
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = connectionColor(status),
                    modifier = Modifier.size(8.dp)
                ) {}
                Text(
                    text = connectionLabel(status),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = connectionColor(status)
                )
            }
        }
    }
}

@Composable
private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCONNECTED -> "Offline"
    ConnectionStatus.CONNECTING -> "Connecting"
    ConnectionStatus.CONNECTED -> "Online"
}

@Composable
private fun connectionColor(status: ConnectionStatus): Color = when (status) {
    ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
    ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
    ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatValue(value: Double, unit: String): String {
    val formatted = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        "%.2f".format(value).trimEnd('0').trimEnd('.')
    }
    return if (unit.isBlank()) formatted else "$formatted $unit"
}
