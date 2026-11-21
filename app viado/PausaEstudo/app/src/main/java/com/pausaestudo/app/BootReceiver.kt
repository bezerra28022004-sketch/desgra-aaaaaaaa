package com.pausaestudo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Religa o monitoramento depois que o celular reinicia (ou o app é atualizado). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (Prefs(context).enabled) {
                MonitorService.start(context)
            }
        } catch (e: Exception) {
            // sem o que fazer aqui
        }
    }
}
