package com.pausaestudo.app

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/**
 * Serviço que conduz o ciclo (pomodoro):
 *
 *   descanso (pelo relógio) -> sessão de estudo -> descanso -> ...
 *
 * Modo PDF: a sessão mostra as próximas páginas do PDF por cima de tudo.
 * Modo APP: a sessão abre um app (ex.: Samsung Notes) e, quando o tempo de
 * estudo acaba, manda uma notificação. Nenhuma contagem fica visível.
 */
class MonitorService : Service() {

    companion object {
        const val ACTION_TEST = "com.pausaestudo.app.ACTION_TEST"
        private const val CH_MONITOR = "monitor"
        private const val CH_ALERT = "alerta_mudo"
        private const val NOTIF_ID = 1
        private const val ALERT_ID = 2

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, MonitorService::class.java))
            } catch (e: Exception) {
                // pode falhar se o sistema não permitir iniciar agora
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }

        fun test(ctx: Context) {
            try {
                ctx.startForegroundService(
                    Intent(ctx, MonitorService::class.java).setAction(ACTION_TEST)
                )
            } catch (e: Exception) {
                // idem
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var pm: PowerManager
    private lateinit var km: KeyguardManager

    private var overlay: StudyOverlay? = null
    private var notice: NoticeOverlay? = null
    private var rest: RestOverlay? = null

    // Último estado mostrado na notificação fixa (para só atualizar quando mudar)
    private var shownStatus = ""

    private val tick = object : Runnable {
        override fun run() {
            try {
                doTick()
            } catch (e: Exception) {
                // nunca deixa o relógio parar
            }
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        createChannels()
        if (prefs.enabled && prefs.dueAt == 0L) prefs.startCycle()
        handler.postDelayed(tick, 2000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent?.action == ACTION_TEST) {
            startSession(true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.dismiss()
        overlay = null
        notice?.cancel()
        notice = null
        rest?.cancel()
        rest = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ ciclo

    private fun doTick() {
        if (!prefs.enabled) return
        updateStatusNotification()
        if (overlay?.isShowing == true) return   // estudando (PDF na tela)
        if (notice?.isShowing == true) return    // abrindo o app de estudo
        if (rest?.isShowing == true) return      // tela de descanso aberta

        val now = System.currentTimeMillis()

        // Modo app: está estudando, esperando o tempo (escondido) acabar.
        if (prefs.phase == Prefs.PHASE_STUDYING) {
            if (now >= prefs.studyEndAt) finishAppStudy()
            return
        }

        // Descanso acabou: abre a sessão assim que a tela estiver ligada e destravada.
        if (now >= prefs.dueAt && pm.isInteractive && !km.isKeyguardLocked) {
            startSession(false)
        }
    }

    private fun postpone(minutes: Int) {
        prefs.dueAt = System.currentTimeMillis() + minutes * 60_000L
    }

    private fun startSession(test: Boolean) {
        if (overlay?.isShowing == true || notice?.isShowing == true) return
        if (!Settings.canDrawOverlays(this)) {
            if (!test) postpone(5)
            return
        }
        if (prefs.mode == Prefs.MODE_APP) startAppSession(test) else startPdfSession(test)
    }

    // ------------------------------------------------------------- modo PDF

    private fun startPdfSession(test: Boolean) {
        val o = StudyOverlay(this, prefs, test) { lastPage ->
            overlay = null
            if (!test) {
                if (prefs.endBy == Prefs.END_TIME) {
                    // sessão por tempo: guarda a página onde parou e avisa que acabou
                    prefs.nextPage = lastPage
                    announceRest()
                } else {
                    prefs.advancePage()
                }
                prefs.startCycle()
                if (prefs.goHome) goHome()
            }
            if (!prefs.enabled) stopSelf()
        }
        overlay = o
        o.show()
        if (!o.isShowing) {
            overlay = null
            if (!test) postpone(1)
        }
    }

    // ------------------------------------------------------------- modo app

    private fun startAppSession(test: Boolean) {
        val pkg = prefs.studyApp
        val launch = if (pkg.isEmpty()) null else packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            if (!test) {
                postpone(10)
                notifyAlert(
                    "Escolha o app de estudo",
                    "Abra o Pausa Estudo e escolha qual app deve abrir."
                )
            }
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!test) {
            prefs.phase = Prefs.PHASE_STUDYING
            prefs.studyEndAt = System.currentTimeMillis() + prefs.studyMinutes * 60_000L
        }

        val n = NoticeOverlay(this)
        notice = n
        n.showAndRun(
            prefs.message,
            {
                try {
                    startActivity(launch)
                } catch (e: Exception) {
                    // ignora
                }
            },
            {
                notice = null
                if (!prefs.enabled) stopSelf()
            }
        )
        if (!n.isShowing) {
            // não deu para mostrar o aviso: tenta abrir direto
            notice = null
            try {
                startActivity(launch)
            } catch (e: Exception) {
                // ignora
            }
        }
    }

    private fun finishAppStudy() {
        prefs.startCycle()
        announceRest()
        updateStatusNotification()
    }

    /** Estudo acabou: tela de descanso + som + notificação (dá para perceber até sem fone). */
    private fun announceRest() {
        val title = prefs.restMessage
        val text = "Seu tempo de estudo acabou. Descanse um pouco!"
        notifyAlert(title, text)
        if (!Settings.canDrawOverlays(this)) return
        rest?.cancel()
        val r = RestOverlay(this)
        rest = r
        r.show(title, text, prefs.sound) { rest = null }
        if (!r.isShowing) rest = null
    }

    private fun goHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
        } catch (e: Exception) {
            // ignora
        }
    }

    // ------------------------------------------------------------ notificação

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val monitor = NotificationChannel(CH_MONITOR, "Monitoramento", NotificationManager.IMPORTANCE_LOW)
        monitor.description = "Mantém o Pausa Estudo funcionando em segundo plano"
        nm.createNotificationChannel(monitor)

        // canal antigo (com som próprio) foi trocado: o som agora é tocado pela tela de descanso
        nm.deleteNotificationChannel("alerta")
        val alert = NotificationChannel(CH_ALERT, "Avisos de estudo", NotificationManager.IMPORTANCE_HIGH)
        alert.description = "Avisa quando o tempo de estudo acaba"
        alert.setSound(null, null)
        alert.enableVibration(false)
        nm.createNotificationChannel(alert)
    }

    private fun openAppIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Notificação fixa com o tempo restante. Fica só na barra de notificações
     * (abaixe a barra para ver), nunca por cima do que você está fazendo.
     */
    private fun buildStatusNotification(): Pair<Notification, String> {
        val now = System.currentTimeMillis()
        var title = "Pausa Estudo ativo"
        var text = "Ciclo de estudo ligado"
        var target = 0L

        if (prefs.enabled) {
            val o = overlay
            if (o?.isShowing == true) {
                title = "Estudando 📚"
                if (o.endsAt > now) {
                    text = "Tempo restante até o descanso"
                    target = o.endsAt
                } else {
                    text = "Termine o trecho para liberar o descanso"
                }
            } else if (prefs.phase == Prefs.PHASE_STUDYING) {
                title = "Estudando 📚"
                if (prefs.studyEndAt > now) {
                    text = "Tempo restante até o descanso"
                    target = prefs.studyEndAt
                } else {
                    text = "Acabando o estudo…"
                }
            } else {
                title = "Descansando ☕"
                if (prefs.dueAt > now) {
                    text = "Tempo restante até estudar"
                    target = prefs.dueAt
                } else {
                    text = "Hora de estudar: abre assim que a tela estiver destravada"
                }
            }
        }

        val b = Notification.Builder(this, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (target > 0L) {
            b.setShowWhen(true)
                .setWhen(target)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }
        // chave do estado: só reenvia a notificação quando algo mudou
        val key = "$title|$text|$target"
        return Pair(b.build(), key)
    }

    private fun updateStatusNotification() {
        val (n, key) = buildStatusNotification()
        if (key == shownStatus) return
        shownStatus = key
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        } catch (e: Exception) {
            // ignora
        }
    }

    private fun goForeground() {
        val (n, key) = buildStatusNotification()
        shownStatus = key
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notifyAlert(title: String, text: String) {
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(ALERT_ID, n)
    }
}
