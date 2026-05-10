package com.frodrigues.odbmqtt.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.frodrigues.odbmqtt.service.ConnectionStatus
import com.frodrigues.odbmqtt.service.OBDCollectorService
import com.frodrigues.odbmqtt.service.ServiceStatus
import com.frodrigues.odbmqtt.settings.AppSettings
import com.frodrigues.odbmqtt.settings.dataStore
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settings = AppSettings(app.dataStore)

    val serviceStatus: StateFlow<ServiceStatus> = OBDCollectorService.status
    val activePidCount: StateFlow<Int> = OBDCollectorService.activePidCount
    val lastUpdateTime: StateFlow<Long> = OBDCollectorService.lastUpdateTime
    val btStatus: StateFlow<ConnectionStatus> = OBDCollectorService.btStatus
    val mqttStatus: StateFlow<ConnectionStatus> = OBDCollectorService.mqttStatus
    val pidReadings: StateFlow<Map<Int, Double>> = OBDCollectorService.pidReadings

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
