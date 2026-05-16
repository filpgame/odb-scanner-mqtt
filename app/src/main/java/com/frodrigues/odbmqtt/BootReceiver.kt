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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autoStart = AppSettings(context.dataStore).autoStart.first()
                if (autoStart) {
                    val serviceIntent = Intent(context, OBDCollectorService::class.java).apply {
                        action = OBDCollectorService.ACTION_START
                    }
                    context.startForegroundService(serviceIntent)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
