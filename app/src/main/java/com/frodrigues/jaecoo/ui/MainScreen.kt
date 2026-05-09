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

@OptIn(ExperimentalMaterial3Api::class)
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

            val isRunning = status == ServiceStatus.CONNECTED ||
                            status == ServiceStatus.CONNECTING ||
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
