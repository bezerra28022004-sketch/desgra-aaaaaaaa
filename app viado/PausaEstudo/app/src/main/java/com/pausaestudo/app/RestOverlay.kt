package com.pausaestudo.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tela cheia "Hora do descanso" que aparece por cima de tudo quando o estudo acaba.
 * Toca um som (canal de ALARME, então sai no alto-falante e funciona sem fone) e vibra.
 * Fica na tela até a pessoa tocar em OK (ou some sozinha depois de 2 minutos).
 */
class RestOverlay(private val ctx: Context) {

    var isShowing = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: LinearLayout? = null
    private var ringtone: Ringtone? = null

    fun show(title: String, text: String, withSound: Boolean, onClosed: () -> Unit) {
        if (isShowing) return

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setBackgroundColor(Color.parseColor("#1B5E20"))
        col.setPadding(ctx.dp(24), ctx.dp(24), ctx.dp(24), ctx.dp(24))

        val tvTitle = TextView(ctx)
        tvTitle.text = title
        tvTitle.setTextColor(Color.WHITE)
        tvTitle.textSize = 32f
        tvTitle.setTypeface(tvTitle.typeface, Typeface.BOLD)
        tvTitle.gravity = Gravity.CENTER
        col.addView(tvTitle, LinearLayout.LayoutParams(MATCH, WRAP))

        val tvText = TextView(ctx)
        tvText.text = text
        tvText.setTextColor(Color.parseColor("#C8E6C9"))
        tvText.textSize = 16f
        tvText.gravity = Gravity.CENTER
        tvText.setPadding(0, ctx.dp(12), 0, ctx.dp(32))
        col.addView(tvText, LinearLayout.LayoutParams(MATCH, WRAP))

        val btn = Button(ctx)
        btn.text = "OK, vou descansar"
        btn.setAllCaps(false)
        btn.setTextColor(Color.parseColor("#1B5E20"))
        val bg = GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = ctx.dp(12).toFloat()
        btn.background = bg
        btn.setOnClickListener {
            cancel()
            onClosed()
        }
        col.addView(btn, LinearLayout.LayoutParams(MATCH, ctx.dp(56)))

        val lp = WindowManager.LayoutParams(
            MATCH,
            MATCH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )

        try {
            wm.addView(col, lp)
        } catch (e: Exception) {
            return
        }
        view = col
        isShowing = true

        if (withSound) {
            playSound()
            vibrate()
        }

        handler.postDelayed({
            cancel()
            onClosed()
        }, 120_000L)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        if (!isShowing) return
        isShowing = false
        try {
            view?.let { wm.removeView(it) }
        } catch (e: Exception) {
            // já removida
        }
        view = null
    }

    private fun playSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(ctx, uri) ?: return
            r.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            r.play()
            ringtone = r
            // não deixa tocando para sempre
            handler.postDelayed({ stopSound() }, 8_000L)
        } catch (e: Exception) {
            // sem som, mas a tela continua aparecendo
        }
    }

    private fun stopSound() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            // ignora
        }
        ringtone = null
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
            }
        } catch (e: Exception) {
            // ignora
        }
    }
}
