package com.frodrigues.odbmqtt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frodrigues.odbmqtt.obd.PidPoller
import com.frodrigues.odbmqtt.obd.PidRegistry
import com.frodrigues.odbmqtt.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PidSelectionScreen(
    settings: AppSettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val cachedPids by settings.cachedPids.collectAsState(initial = emptySet())

    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cachedSet = settings.cachedPids.first()
        val userSet = settings.selectedPids.first()
        selected = userSet ?: cachedSet
        initialized = true
    }

    fun toggle(pid: Int) {
        selected = if (pid in selected) selected - pid else selected + pid
        val current = selected
        scope.launch {
            val allDiscovered = settings.cachedPids.first()
            settings.setSelectedPids(if (current == allDiscovered) null else current)
        }
    }

    fun selectAll() {
        selected = cachedPids
        scope.launch { settings.setSelectedPids(null) }
    }

    fun deselectAll() {
        selected = emptySet()
        scope.launch { settings.setSelectedPids(emptySet()) }
    }

    val fastPids = cachedPids.intersect(PidPoller.FAST_PIDS).sortedBy {
        PidRegistry.getOrUnknown(it).name
    }
    val slowPids = (cachedPids - PidPoller.FAST_PIDS).sortedBy {
        PidRegistry.getOrUnknown(it).name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PID Selection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        if (!initialized) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (cachedPids.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No PIDs discovered yet. Start the service with the car connected first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Summary + bulk actions ────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selected.size} of ${cachedPids.size} PIDs selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Only selected PIDs are polled and sent to MQTT",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { selectAll() }, modifier = Modifier.weight(1f)) {
                        Text("Select All")
                    }
                    TextButton(onClick = { deselectAll() }, modifier = Modifier.weight(1f)) {
                        Text("Deselect All")
                    }
                }
            }

            // ── Fast PIDs ─────────────────────────────────────────────────────
            if (fastPids.isNotEmpty()) {
                Text(
                    text = "Fast — every cycle",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    fastPids.forEachIndexed { index, pid ->
                        if (index > 0) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        PidRow(
                            pid = pid,
                            checked = pid in selected,
                            onToggle = { toggle(pid) }
                        )
                    }
                }
            }

            // ── Slow PIDs ─────────────────────────────────────────────────────
            if (slowPids.isNotEmpty()) {
                Text(
                    text = "Slow — every ~6 cycles",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    slowPids.forEachIndexed { index, pid ->
                        if (index > 0) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        PidRow(
                            pid = pid,
                            checked = pid in selected,
                            onToggle = { toggle(pid) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PidRow(pid: Int, checked: Boolean, onToggle: () -> Unit) {
    val def = PidRegistry.getOrUnknown(pid)
    ListItem(
        headlineContent = { Text(def.name, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text(
                text = "0x${pid.toString(16).padStart(2, '0').uppercase()}" +
                       if (def.unit.isNotBlank()) " · ${def.unit}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(role = Role.Checkbox, onClickLabel = def.name) { onToggle() }
    )
}
