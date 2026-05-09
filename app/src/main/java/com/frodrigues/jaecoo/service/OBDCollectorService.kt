package com.frodrigues.jaecoo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.ServiceInfo
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

enum class ServiceStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING }

class OBDCollectorService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun getService(): OBDCollectorService = this@OBDCollectorService
    }

    private val binder = LocalBinder()

    private val _status = MutableStateFlow(ServiceStatus.IDLE)
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private val _activePidCount = MutableStateFlow(0)
    val activePidCount: StateFlow<Int> = _activePidCount.asStateFlow()

    private val _lastUpdateTime = MutableStateFlow(0L)
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime.asStateFlow()

    private lateinit var settings: AppSettings
    private var collectorJob: Job? = null
    private var currentStateTopics: List<String> = emptyList()
    @Volatile private var mqttPublisherRef: MqttPublisher? = null

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
                _status.value = ServiceStatus.RECONNECTING
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

        _status.value = ServiceStatus.CONNECTING
        updateNotification("Connecting to Bluetooth...")

        @Suppress("DEPRECATION")
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
            _activePidCount.value = supportedPids.size

            updateNotification("Connecting to MQTT...")
            val mqttPublisher = MqttPublisher(config)
            mqttPublisher.connect()
            mqttPublisherRef = mqttPublisher

            val mac = device.address.replace(":", "")
            currentStateTopics = supportedPids.map { pid ->
                "obd2/$mac/${pid.toString(16).padStart(2, '0').uppercase()}/state"
            }

            HaDiscoveryPublisher(
                publish = { t, p -> mqttPublisher.publish(t, p, retain = true) },
                config = config
            ).publishDiscovery(supportedPids, mac)

            _status.value = ServiceStatus.CONNECTED
            updateNotification("Connected — ${supportedPids.size} PIDs")

            PidPoller(executor, supportedPids, config.pollIntervalSeconds).readings().collect { reading ->
                val pidHex = reading.pid.toString(16).padStart(2, '0').uppercase()
                val value = reading.value.toBigDecimal().stripTrailingZeros().toPlainString()
                mqttPublisher.publish("obd2/$mac/$pidHex/state", value, retain = true)
                _lastUpdateTime.value = reading.timestamp
            }
        } finally {
            publishUnavailable()
            mqttPublisherRef?.disconnect()
            mqttPublisherRef = null
            transport.disconnect()
        }
    }

    private fun stopCollection() {
        val job = collectorJob ?: return
        collectorJob = null
        job.invokeOnCompletion {
            _status.value = ServiceStatus.IDLE
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
        const val ACTION_START = "com.frodrigues.jaecoo.START"
        const val ACTION_STOP = "com.frodrigues.jaecoo.STOP"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "obd_collector"
    }
}
