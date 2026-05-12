package com.frodrigues.odbmqtt.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.frodrigues.odbmqtt.MainActivity
import com.frodrigues.odbmqtt.bluetooth.BluetoothTransportFactory
import com.frodrigues.odbmqtt.mqtt.HaDiscoveryPublisher
import com.frodrigues.odbmqtt.mqtt.MqttPublisher
import com.frodrigues.odbmqtt.obd.Mode22Registry
import com.frodrigues.odbmqtt.obd.Mode22Scanner
import com.frodrigues.odbmqtt.obd.ObdCommandExecutor
import com.frodrigues.odbmqtt.obd.PidPoller
import com.frodrigues.odbmqtt.obd.PidScanner
import com.frodrigues.odbmqtt.settings.AppSettings
import com.frodrigues.odbmqtt.settings.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

enum class ServiceStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING }
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

class OBDCollectorService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun getService(): OBDCollectorService = this@OBDCollectorService
    }

    private val binder = LocalBinder()

    private lateinit var settings: AppSettings
    private var collectorJob: Job? = null
    private var currentStateTopics: List<String> = emptyList()
    @Volatile private var mqttPublisherRef: MqttPublisher? = null

    override fun onCreate() {
        super.onCreate()
        status.value = ServiceStatus.IDLE
        activePidCount.value = 0
        lastUpdateTime.value = 0L
        btStatus.value = ConnectionStatus.DISCONNECTED
        mqttStatus.value = ConnectionStatus.DISCONNECTED
        pidReadings.value = emptyMap()
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
            ACTION_START, null -> startCollection()
            ACTION_STOP -> stopCollection()
        }
        return START_STICKY
    }

    private fun startCollection() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification("Starting..."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
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
                btStatus.value = ConnectionStatus.DISCONNECTED
                mqttStatus.value = ConnectionStatus.DISCONNECTED
                val delay = backoffMs.getOrElse(attempt) { 60_000L }
                attempt = minOf(attempt + 1, backoffMs.lastIndex)
                updateNotification("Reconnecting in ${delay / 1000}s...")
                delay(delay)
            }
        }
    }

    private suspend fun collect() {
        val config = settings.snapshot()
        if (config.btDeviceMac.isBlank()) throw IllegalStateException("No BT device configured")
        if (config.mqttHost.isBlank()) throw IllegalStateException("No MQTT host configured")

        status.value = ServiceStatus.CONNECTING
        btStatus.value = ConnectionStatus.CONNECTING
        updateNotification("Connecting to Bluetooth...")

        val bluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IllegalStateException("Bluetooth not available")

        val device = bluetoothAdapter.getRemoteDevice(config.btDeviceMac)
        val transport = BluetoothTransportFactory.create(device, applicationContext)
        transport.connect()

        try {
            updateNotification("Initializing ELM327...")
            val executor = ObdCommandExecutor(transport)
            executor.initialize()

            updateNotification("Scanning PIDs...")
            val supportedPids = PidScanner(
                executor = executor,
                settings = settings,
                onProgress = { scanned, total ->
                    updateNotification("Scanning PIDs ($scanned/$total)...")
                }
            ).scan()
            activePidCount.value = supportedPids.size
            btStatus.value = ConnectionStatus.CONNECTED

            mqttStatus.value = ConnectionStatus.CONNECTING
            updateNotification("Connecting to MQTT...")
            val mqttPublisher = MqttPublisher(config)
            mqttPublisher.connect()
            mqttStatus.value = ConnectionStatus.CONNECTED
            mqttPublisherRef = mqttPublisher

            val mac = device.address.replace(":", "")
            currentStateTopics = supportedPids.map { pid ->
                "obd2/$mac/${pid.toString(16).padStart(2, '0').uppercase()}/state"
            }

            HaDiscoveryPublisher(
                publish = { t, p -> mqttPublisher.publish(t, p, retain = true) },
                config = config
            ).publishDiscovery(supportedPids, mac)

            // ── Mode 22 scan ─────────────────────────────────────────────────────
            updateNotification("Scanning Mode 22 PIDs...")
        val mode22Pids = Mode22Scanner(
            executor = executor,
            settings = settings,
            onProgress = { scanned, total ->
                updateNotification("Scanning Mode 22 ($scanned/$total)...")
            }
        ).scan()

        // Publish Mode 22 HA Discovery
        mode22Pids.keys.forEach { pid ->
            val def = Mode22Registry.getOrUnknown(pid)
            val pidHex = pid.toString(16).padStart(4, '0').uppercase()
            val topic = "homeassistant/sensor/obd2_${mac}/m22_$pidHex/config"
            val stateTopic = "obd2/$mac/m22_$pidHex/state"
            val payload = buildString {
                append("{")
                append("\"name\":\"${config.deviceName} ${def.name}\"")
                append(",\"state_topic\":\"$stateTopic\"")
                if (def.unit.isNotBlank()) append(",\"unit_of_measurement\":\"${def.unit}\"")
                append(",\"unique_id\":\"${mac}_m22_$pidHex\"")
                def.haDeviceClass?.let { append(",\"device_class\":\"$it\"") }
                append(",\"device\":{\"identifiers\":[\"obd2_$mac\"],\"name\":\"${config.deviceName}\"}")
                append("}")
            }
            mqttPublisher.publish(topic, payload, retain = true)
        }

        status.value = ServiceStatus.CONNECTED
        updateNotification("Connected — ${supportedPids.size} M01 + ${mode22Pids.size} M22 PIDs")

        val userSelected: Set<Int>? = settings.selectedPids.first()
        val activePids: Set<Int> = if (userSelected == null) supportedPids else supportedPids.intersect(userSelected)

        val fastPids = activePids.intersect(PidPoller.FAST_PIDS)
        val slowPids = activePids - fastPids
        status.value = ServiceStatus.CONNECTED
        updateNotification("Connected — ${fastPids.size} fast + ${slowPids.size} slow PIDs")

        val currentReadings = mutableMapOf<Int, Double>()

        PidPoller(
            executor = executor,
            fastPids = fastPids,
            slowPids = slowPids,
            intervalSeconds = config.pollIntervalSeconds
        ).readings().collect { reading ->
            currentReadings[reading.pid] = reading.value
            pidReadings.value = currentReadings.toMap()
            val pidHex = reading.pid.toString(16).padStart(2, '0').uppercase()
            val value = reading.value.toBigDecimal().stripTrailingZeros().toPlainString()
            mqttPublisher.publish("obd2/$mac/$pidHex/state", value, retain = true)
            lastUpdateTime.value = reading.timestamp
        }
        } finally {
            publishUnavailable()
            mqttPublisherRef?.disconnect()
            mqttPublisherRef = null
            transport.disconnect()
            btStatus.value = ConnectionStatus.DISCONNECTED
            mqttStatus.value = ConnectionStatus.DISCONNECTED
            pidReadings.value = emptyMap()
        }
    }

    private fun stopCollection() {
        val job = collectorJob ?: return
        collectorJob = null
        job.invokeOnCompletion {
            status.value = ServiceStatus.IDLE
            btStatus.value = ConnectionStatus.DISCONNECTED
            mqttStatus.value = ConnectionStatus.DISCONNECTED
            pidReadings.value = emptyMap()
        }
        job.cancel()
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
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.frodrigues.odbmqtt.START"
        const val ACTION_STOP = "com.frodrigues.odbmqtt.STOP"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "obd_collector"

        val status = MutableStateFlow(ServiceStatus.IDLE)
        val activePidCount = MutableStateFlow(0)
        val lastUpdateTime = MutableStateFlow(0L)
        val btStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val mqttStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val pidReadings = MutableStateFlow<Map<Int, Double>>(emptyMap())
    }
}
