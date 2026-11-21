package com.pausaestudo.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Aviso rápido em tela cheia. O Android só deixa um app abrir outro app "do nada"
 * se ele tiver uma janela visível, então o aviso aparece, o outro app é aberto
 * enquanto ele está na tela, e depois ele some.
 */
class NoticeOverlay(private val ctx: Context) {

    var isShowing = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null

    fun showAndRun(message: String, action: () -> Unit, onDone: () -> Unit) {
        if (isShowing) return

        val tv = TextView(ctx)
        tv.text = message
        tv.setTextColor(Color.WHITE)
        tv.textSize = 26f
        tv.gravity = Gravity.CENTER
        tv.setBackgroundColor(Color.parseColor("#12163A"))
        tv.setPadding(ctx.dp(24), ctx.dp(24), ctx.dp(24), ctx.dp(24))

        val lp = WindowManager.LayoutParams(
            MATCH,
            MATCH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.OPAQUE
        )

        try {
            wm.addView(tv, lp)
        } catch (e: Exception) {
            return
        }
        view = tv
        isShowing = true

        handler.postDelayed({
            try {
                action()
            } catch (e: Exception) {
                // ignora
            }
            handler.postDelayed({
                cancel()
                onDone()
            }, 1200L)
        }, 1500L)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        if (!isShowing) return
        isShowing = false
        try {
            view?.let { wm.removeView(it) }
        } catch (e: Exception) {
            // já removido
        }
        view = null
    }
}
