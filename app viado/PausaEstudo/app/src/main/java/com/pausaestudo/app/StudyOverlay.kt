package com.pausaestudo.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Raiz da tela: engole o botão "voltar" para a pessoa não fechar sem estudar. */
private class OverlayRoot(ctx: Context) : FrameLayout(ctx) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Tela cheia desenhada POR CIMA de qualquer app (inclusive o TikTok).
 * Mostra o trecho do PDF e só libera o botão "Terminei" depois do tempo mínimo.
 */
class StudyOverlay(
    private val ctx: Context,
    private val prefs: Prefs,
    private val test: Boolean,
    private val onFinished: (Int) -> Unit
) {
    /** Sessão por tempo: acaba sozinha, sem botão de terminar e sem contagem visível. */
    private val timed = prefs.endBy == Prefs.END_TIME

    var isShowing = false
        private set

    /** Momento previsto para a sessão por tempo acabar (0 se não for por tempo). Usado na notificação. */
    var endsAt = 0L
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var rootView: OverlayRoot? = null
    private lateinit var scroll: ScrollView
    private lateinit var pageImage: ImageView
    private lateinit var info: TextView
    private lateinit var pageLabel: TextView
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var finishBtn: Button

    @Volatile
    private var renderer: PdfRenderer? = null
    private var pageList: List<Int> = emptyList()
    private var current = 0
    private var remaining = 0
    private var startIndex = 0

    private val countdown = object : Runnable {
        override fun run() {
            if (!isShowing) return
            if (remaining > 0) {
                remaining--
                if (remaining > 0) {
                    updateFinishButton()
                    handler.postDelayed(this, 1000L)
                } else if (timed) {
                    finishNow()          // o tempo acabou: fecha sozinho
                } else {
                    updateFinishButton() // libera o botão "Terminei"
                }
            }
        }
    }

    // ------------------------------------------------------------------ ciclo

    fun show() {
        if (isShowing) return

        val root = OverlayRoot(ctx)
        root.setBackgroundColor(Color.parseColor("#12163A"))
        root.addView(buildContent(), FrameLayout.LayoutParams(MATCH, MATCH))

        val lp = WindowManager.LayoutParams(
            MATCH,
            MATCH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )

        try {
            wm.addView(root, lp)
        } catch (e: Exception) {
            executor.shutdown()
            return
        }

        rootView = root
        isShowing = true
        root.isFocusableInTouchMode = true
        root.requestFocus()

        remaining = if (timed) {
            if (test) 10 else prefs.studyMinutes * 60   // no teste, só 10 segundos
        } else {
            prefs.minReadSeconds
        }
        if (timed) endsAt = System.currentTimeMillis() + remaining * 1000L
        updateFinishButton()
        if (remaining > 0) handler.postDelayed(countdown, 1000L)

        loadPdf()
    }

    fun dismiss() {
        if (!isShowing) return
        isShowing = false
        handler.removeCallbacksAndMessages(null)
        try {
            rootView?.let { wm.removeView(it) }
        } catch (e: Exception) {
            // já removida
        }
        rootView = null
        try {
            executor.execute {
                try {
                    renderer?.close()
                } catch (e: Exception) {
                    // ignora
                }
                renderer = null
            }
            executor.shutdown()
        } catch (e: Exception) {
            // ignora
        }
    }

    private fun finishNow() {
        if (!isShowing || remaining > 0) return
        try {
            onFinished(currentPageNumber())
        } catch (e: Exception) {
            // mesmo se algo falhar, a tela precisa fechar
        }
        dismiss()
    }

    // --------------------------------------------------------------------- UI

    /** Página (começando em 1) que estava na tela. */
    private fun currentPageNumber(): Int {
        return if (pageList.isEmpty()) prefs.nextPage else pageList[current] + 1
    }

    private fun dp(v: Int): Int = ctx.dp(v)

    private fun sysDimen(name: String, fallbackDp: Int): Int {
        val id = ctx.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(fallbackDp)
    }

    private fun buildContent(): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(
            dp(16),
            sysDimen("status_bar_height", 24) + dp(8),
            dp(16),
            maxOf(sysDimen("navigation_bar_height", 0), dp(8)) + dp(8)
        )

        val title = TextView(ctx)
        title.text = prefs.message
        title.setTextColor(Color.WHITE)
        title.textSize = 26f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.gravity = Gravity.CENTER
        col.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))

        val sub = TextView(ctx)
        val range = prefs.currentRange()
        val lastPart = !prefs.wholePdf && prefs.pdfPages > 0 && range.last >= prefs.pdfPages
        sub.text = if (timed) {
            "Continue estudando até o tempo acabar."
        } else if (prefs.wholePdf) {
            "Leia o PDF antes de voltar."
        } else if (lastPart) {
            "Última parte do PDF! 🎉 Depois dela, recomeça da página 1."
        } else {
            "Leia o trecho abaixo antes de voltar."
        }
        sub.setTextColor(Color.parseColor("#B0B7E8"))
        sub.textSize = 14f
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(4), 0, dp(10))
        col.addView(sub, LinearLayout.LayoutParams(MATCH, WRAP))

        info = TextView(ctx)
        info.text = "Carregando…"
        info.setTextColor(Color.WHITE)
        info.textSize = 16f
        info.gravity = Gravity.CENTER
        info.setPadding(0, dp(12), 0, dp(12))
        col.addView(info, LinearLayout.LayoutParams(MATCH, WRAP))

        scroll = ScrollView(ctx)
        pageImage = ImageView(ctx)
        pageImage.adjustViewBounds = true
        pageImage.setBackgroundColor(Color.WHITE)
        scroll.addView(pageImage, ViewGroup.LayoutParams(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val nav = LinearLayout(ctx)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.setPadding(0, dp(10), 0, dp(6))

        prevBtn = makeButton("‹ Anterior", "#3949AB") { goTo(current - 1) }
        nextBtn = makeButton("Próxima ›", "#3949AB") { goTo(current + 1) }
        pageLabel = TextView(ctx)
        pageLabel.setTextColor(Color.WHITE)
        pageLabel.textSize = 13f
        pageLabel.gravity = Gravity.CENTER

        nav.addView(prevBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        nav.addView(pageLabel, LinearLayout.LayoutParams(0, WRAP, 1f))
        nav.addView(nextBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(nav, LinearLayout.LayoutParams(MATCH, WRAP))

        finishBtn = makeButton("Terminei de estudar ✓", "#2E7D32") { finishNow() }
        col.addView(finishBtn, LinearLayout.LayoutParams(MATCH, dp(56)))
        if (timed) finishBtn.visibility = View.GONE

        setEnabledLook(prevBtn, false)
        setEnabledLook(nextBtn, false)
        return col
    }

    private fun makeButton(label: String, color: String, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.setAllCaps(false)
        b.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor(color))
        bg.cornerRadius = dp(12).toFloat()
        b.background = bg
        b.setOnClickListener { onClick() }
        return b
    }

    private fun setEnabledLook(b: Button, enabled: Boolean) {
        b.isEnabled = enabled
        b.alpha = if (enabled) 1f else 0.4f
    }

    private fun updateFinishButton() {
        if (remaining > 0) {
            setEnabledLook(finishBtn, false)
            finishBtn.text = "Continue estudando…"
        } else {
            setEnabledLook(finishBtn, true)
            finishBtn.text = "Terminei de estudar ✓"
        }
    }

    private fun updateNav() {
        if (pageList.isEmpty()) return
        setEnabledLook(prevBtn, current > 0)
        setEnabledLook(nextBtn, current < pageList.size - 1)
        pageLabel.text = "Página ${pageList[current] + 1}\n(${current + 1} de ${pageList.size})"
    }

    private fun showError(msg: String) {
        info.text = msg
        info.visibility = View.VISIBLE
        pageLabel.text = ""
    }

    // -------------------------------------------------------------------- PDF

    private fun loadPdf() {
        executor.execute {
            var error: String? = null
            try {
                val f = prefs.pdfFile
                if (!f.exists() || f.length() == 0L) {
                    error = "Nenhum PDF configurado.\nAbra o app Pausa Estudo e escolha um PDF."
                } else {
                    val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(pfd)
                    renderer = r
                    val total = r.pageCount
                    if (total <= 0) {
                        error = "O PDF não tem páginas."
                    } else {
                        val range = prefs.currentRange()
                        val from = (range.first - 1).coerceIn(0, total - 1)
                        val to = (range.last - 1).coerceIn(from, total - 1)
                        pageList = (from..to).toList()
                        // sessão por tempo: continua de onde parou da última vez
                        startIndex = if (timed) pageList.indexOf(prefs.nextPage - 1).coerceAtLeast(0) else 0
                    }
                }
            } catch (e: Exception) {
                error = "Não consegui abrir o PDF: ${e.message}"
            }
            val finalError = error
            handler.post {
                if (isShowing) {
                    if (finalError != null) {
                        showError(finalError)
                    } else {
                        current = startIndex
                        renderPage()
                    }
                }
            }
        }
    }

    private fun goTo(i: Int) {
        if (!isShowing || i < 0 || i >= pageList.size) return
        current = i
        renderPage()
    }

    private fun renderPage() {
        if (!isShowing || pageList.isEmpty()) return
        updateNav()
        val pageIndex = pageList[current]
        val width = ctx.resources.displayMetrics.widthPixels

        executor.execute {
            val r = renderer ?: return@execute
            try {
                val page = r.openPage(pageIndex)
                val bmp = try {
                    val scale = minOf(width.toFloat() / page.width, 4000f / page.height)
                    val bw = (page.width * scale).toInt().coerceAtLeast(1)
                    val bh = (page.height * scale).toInt().coerceAtLeast(1)
                    val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b
                } finally {
                    page.close()
                }
                handler.post {
                    if (isShowing) {
                        info.visibility = View.GONE
                        pageImage.setImageBitmap(bmp)
                        scroll.scrollTo(0, 0)
                    }
                }
            } catch (e: Exception) {
                handler.post { if (isShowing) showError("Erro ao mostrar a página: ${e.message}") }
            }
        }
    }
}
