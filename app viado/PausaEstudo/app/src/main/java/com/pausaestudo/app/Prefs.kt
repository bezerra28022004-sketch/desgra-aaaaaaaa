package com.pausaestudo.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/** Todas as configurações e o estado do ciclo ficam aqui. */
class Prefs(context: Context) {

    private val appCtx: Context = context.applicationContext ?: context
    private val sp: SharedPreferences =
        appCtx.getSharedPreferences("pausa_estudo", Context.MODE_PRIVATE)

    /** Cópia do PDF escolhido, guardada dentro do app. */
    val pdfFile: File
        get() = File(appCtx.filesDir, "estudo.pdf")

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) {
            sp.edit().putBoolean("enabled", v).apply()
        }

    /** MODE_PDF: mostra páginas do PDF. MODE_APP: abre um app (ex.: Samsung Notes) e avisa por tempo. */
    var mode: Int
        get() = sp.getInt("mode", MODE_PDF)
        set(v) {
            sp.edit().putInt("mode", v).apply()
        }

    /** No PDF: END_PAGES = termina quando a pessoa toca em "Terminei"; END_TIME = termina sozinho por tempo (contagem escondida). */
    var endBy: Int
        get() = sp.getInt("end_by", END_PAGES)
        set(v) {
            sp.edit().putInt("end_by", v).apply()
        }

    /** Abre o PDF inteiro de uma vez, em vez de só algumas páginas por sessão. */
    var wholePdf: Boolean
        get() = sp.getBoolean("whole_pdf", false)
        set(v) {
            sp.edit().putBoolean("whole_pdf", v).apply()
        }

    /** Descanso entre as sessões (conta pelo relógio, mesmo com a tela apagada). */
    var breakMinutes: Int
        get() = sp.getInt("break_minutes", 10)
        set(v) {
            sp.edit().putInt("break_minutes", v).apply()
        }

    /** Modo app: quanto tempo dura o estudo (a contagem fica escondida). */
    var studyMinutes: Int
        get() = sp.getInt("study_minutes", 10)
        set(v) {
            sp.edit().putInt("study_minutes", v).apply()
        }

    /** Modo app: pacote do app que será aberto. */
    var studyApp: String
        get() = sp.getString("study_app", "") ?: ""
        set(v) {
            sp.edit().putString("study_app", v).apply()
        }

    var pdfName: String
        get() = sp.getString("pdf_name", "") ?: ""
        set(v) {
            sp.edit().putString("pdf_name", v).apply()
        }

    var pdfPages: Int
        get() = sp.getInt("pdf_pages", 0)
        set(v) {
            sp.edit().putInt("pdf_pages", v).apply()
        }

    /** Próxima página a estudar. Avança sozinha a cada sessão concluída. */
    var nextPage: Int
        get() = sp.getInt("next_page", 1)
        set(v) {
            sp.edit().putInt("next_page", v).apply()
        }

    var pagesPerSession: Int
        get() = sp.getInt("pages_per_session", 1)
        set(v) {
            sp.edit().putInt("pages_per_session", v).apply()
        }

    /** Segundos que a pessoa precisa esperar antes de poder fechar a tela de estudo. */
    var minReadSeconds: Int
        get() = sp.getInt("min_read_seconds", 30)
        set(v) {
            sp.edit().putInt("min_read_seconds", v).apply()
        }

    var message: String
        get() = sp.getString("message", "Hora de estudar! 📚") ?: "Hora de estudar! 📚"
        set(v) {
            sp.edit().putString("message", v).apply()
        }

    /** Texto da tela de descanso (aparece quando o estudo acaba). */
    var restMessage: String
        get() = sp.getString("rest_message", "Hora do descanso ☕") ?: "Hora do descanso ☕"
        set(v) {
            sp.edit().putString("rest_message", v).apply()
        }

    /** Toca um som (no alto-falante, sem precisar de fone) e vibra quando o estudo acaba. */
    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) {
            sp.edit().putBoolean("sound", v).apply()
        }

    /** Modo PDF: ao terminar, volta para a tela inicial. */
    var goHome: Boolean
        get() = sp.getBoolean("go_home", true)
        set(v) {
            sp.edit().putBoolean("go_home", v).apply()
        }

    // ------------------------------------------------------- estado do ciclo

    /** PHASE_BREAK: descansando até chegar a hora. PHASE_STUDYING: (modo app) estudando, esperando o tempo acabar. */
    var phase: Int
        get() = sp.getInt("phase", PHASE_BREAK)
        set(v) {
            sp.edit().putInt("phase", v).apply()
        }

    /** Momento (em milissegundos) em que a próxima sessão deve abrir. */
    var dueAt: Long
        get() = sp.getLong("due_at", 0L)
        set(v) {
            sp.edit().putLong("due_at", v).apply()
        }

    /** Momento em que o tempo de estudo (modo app) termina. */
    var studyEndAt: Long
        get() = sp.getLong("study_end_at", 0L)
        set(v) {
            sp.edit().putLong("study_end_at", v).apply()
        }

    /** Começa um novo descanso agora. */
    fun startCycle() {
        phase = PHASE_BREAK
        dueAt = System.currentTimeMillis() + breakMinutes * 60_000L
    }

    /** Páginas (começando em 1) que a próxima sessão vai mostrar. */
    fun currentRange(): IntRange {
        val total = pdfPages
        // PDF inteiro, ou sessão por tempo: todas as páginas ficam disponíveis
        if (wholePdf || endBy == END_TIME) return 1..maxOf(total, 1)
        var start = if (nextPage < 1) 1 else nextPage
        if (total > 0 && start > total) start = 1
        val per = if (pagesPerSession < 1) 1 else pagesPerSession
        var end = start + per - 1
        if (total > 0 && end > total) end = total
        return start..end
    }

    /** Depois de estudar: passa para as páginas seguintes (volta ao início no fim do PDF). */
    fun advancePage() {
        if (wholePdf || endBy == END_TIME) return
        val next = currentRange().last + 1
        nextPage = if (pdfPages > 0 && next > pdfPages) 1 else next
    }

    companion object {
        const val MODE_PDF = 0
        const val MODE_APP = 1
        const val PHASE_BREAK = 0
        const val PHASE_STUDYING = 1
        const val END_PAGES = 0
        const val END_TIME = 1
    }
}
