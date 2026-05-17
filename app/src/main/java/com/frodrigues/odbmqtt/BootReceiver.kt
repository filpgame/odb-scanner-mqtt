package com.frodrigues.odbmqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frodrigues.odbmqtt.service.OBDCollectorService
import com.frodrigues.odbmqtt.settings.AppSettings
import com.frodrigues.odbmqtt.settings.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // CE storage unavailable — read autoStart from DE SharedPreferences
                val autoStart = context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(AppSettings.DE_PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(AppSettings.DE_KEY_AUTO_START, false)
                if (autoStart) startService(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val autoStart = AppSettings(context.dataStore).autoStart.first()
                        if (autoStart) startService(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun startService(context: Context) {
        context.startForegroundService(
            Intent(context, OBDCollectorService::class.java).apply {
                action = OBDCollectorService.ACTION_START
            }
        )
    }
}
