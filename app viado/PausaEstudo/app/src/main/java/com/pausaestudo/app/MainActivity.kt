package com.pausaestudo.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    companion object {
        private const val REQ_PDF = 1
        private const val REQ_NOTIF = 2
        private const val SAMSUNG_NOTES = "com.samsung.android.app.notes"
    }

    private lateinit var prefs: Prefs

    private lateinit var tvOverlay: TextView
    private lateinit var tvNotif: TextView
    private lateinit var tvBattery: TextView

    private lateinit var rgMode: RadioGroup
    private lateinit var rbPdf: RadioButton
    private lateinit var rbApp: RadioButton

    private lateinit var endBox: LinearLayout
    private lateinit var rgEnd: RadioGroup
    private lateinit var rbPages: RadioButton
    private lateinit var rbTime: RadioButton

    private lateinit var etBreak: EditText
    private lateinit var etMessage: EditText
    private lateinit var etRestMessage: EditText
    private lateinit var cbSound: CheckBox

    private lateinit var pdfBox: LinearLayout
    private lateinit var tvPdf: TextView
    private lateinit var etNext: EditText
    private lateinit var pagesBox: LinearLayout
    private lateinit var etPerSession: EditText
    private lateinit var cbWhole: CheckBox
    private lateinit var etMinRead: EditText
    private lateinit var cbHome: CheckBox

    private lateinit var appBox: LinearLayout
    private lateinit var tvStudyApp: TextView

    private lateinit var timeBox: LinearLayout
    private lateinit var etStudy: EditText

    private lateinit var swEnable: Switch

    /** Valor da "próxima página" que está na tela, para saber se a pessoa mexeu nele. */
    private var shownNext = 1

    private val onSwitch = CompoundButton.OnCheckedChangeListener { _, checked ->
        toggleMonitoring(checked)
    }

    // ------------------------------------------------------------------ ciclo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        // No tablet Samsung, já deixa o Samsung Notes escolhido como app de estudo
        if (prefs.studyApp.isEmpty() && packageManager.getLaunchIntentForPackage(SAMSUNG_NOTES) != null) {
            prefs.studyApp = SAMSUNG_NOTES
        }
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // se estava ligado e o serviço foi morto pelo sistema, religa
        if (prefs.enabled && Settings.canDrawOverlays(this)) {
            MonitorService.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        saveFields()
    }

    // --------------------------------------------------------------------- UI

    private fun buildUi() {
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(28), dp(20), dp(40))
        scroll.addView(col)
        setContentView(scroll)

        val title = TextView(this)
        title.text = "📚 Pausa Estudo"
        title.textSize = 28f
        title.setTypeface(title.typeface, Typeface.BOLD)
        col.addView(title)

        val intro = TextView(this)
        intro.text = "Ciclo de estudo (pomodoro): você descansa, e depois o app abre sozinho " +
            "a sessão de estudo por cima de tudo."
        intro.textSize = 14f
        intro.setPadding(0, dp(4), 0, dp(8))
        col.addView(intro)

        // permissões
        col.addView(heading("Permissões (conceda as 3)"))
        tvOverlay = statusText()
        tvNotif = statusText()
        tvBattery = statusText()
        permRow(col, "Aparecer sobre outros apps", "Para abrir o estudo por cima de qualquer app", tvOverlay) { openOverlaySettings() }
        permRow(col, "Notificações", "Para avisar que o tempo de estudo acabou", tvNotif) { askNotifications() }
        permRow(col, "Sem otimização de bateria", "Evita que o sistema desligue o app", tvBattery) { askBattery() }

        // o que abrir
        col.addView(heading("O que abrir a cada sessão"))
        rgMode = RadioGroup(this)
        rgMode.orientation = LinearLayout.VERTICAL
        rbPdf = RadioButton(this)
        rbPdf.id = View.generateViewId()
        rbPdf.text = "Um PDF (o app mostra as páginas)"
        rbApp = RadioButton(this)
        rbApp.id = View.generateViewId()
        rbApp.text = "Outro app de estudo (ex.: Samsung Notes)"
        rgMode.addView(rbPdf)
        rgMode.addView(rbApp)
        rgMode.check(if (prefs.mode == Prefs.MODE_APP) rbApp.id else rbPdf.id)
        col.addView(rgMode)

        // como termina (só para PDF)
        endBox = LinearLayout(this)
        endBox.orientation = LinearLayout.VERTICAL
        endBox.addView(heading("Como termina cada sessão"))
        rgEnd = RadioGroup(this)
        rgEnd.orientation = LinearLayout.VERTICAL
        rbPages = RadioButton(this)
        rbPages.id = View.generateViewId()
        rbPages.text = "Por trecho: eu leio e toco em \"Terminei\""
        rbTime = RadioButton(this)
        rbTime.id = View.generateViewId()
        rbTime.text = "Por tempo fixo: acaba sozinha e avisa com uma notificação"
        rgEnd.addView(rbPages)
        rgEnd.addView(rbTime)
        rgEnd.check(if (prefs.endBy == Prefs.END_TIME) rbTime.id else rbPages.id)
        endBox.addView(rgEnd)
        col.addView(endBox)

        // descanso e mensagem
        col.addView(heading("Descanso e mensagem"))
        etBreak = numberField(prefs.breakMinutes)
        col.addView(
            labeledField(
                "Descanso entre as sessões, em minutos (conta o tempo todo, até com a tela apagada ou bloqueada)",
                etBreak
            )
        )
        etMessage = EditText(this)
        etMessage.inputType = InputType.TYPE_CLASS_TEXT
        etMessage.setText(prefs.message)
        col.addView(labeledField("Mensagem na tela (hora de estudar)", etMessage))
        etRestMessage = EditText(this)
        etRestMessage.inputType = InputType.TYPE_CLASS_TEXT
        etRestMessage.setText(prefs.restMessage)
        col.addView(labeledField("Mensagem na tela (hora do descanso)", etRestMessage))
        cbSound = CheckBox(this)
        cbSound.text = "Tocar som e vibrar quando o estudo acabar (toca no alto-falante, não precisa de fone)"
        cbSound.isChecked = prefs.sound
        col.addView(cbSound)

        // PDF
        pdfBox = LinearLayout(this)
        pdfBox.orientation = LinearLayout.VERTICAL
        pdfBox.addView(heading("PDF"))
        tvPdf = TextView(this)
        tvPdf.textSize = 14f
        pdfBox.addView(tvPdf)
        pdfBox.addView(actionButton("Escolher PDF") { pickPdf() })

        etNext = numberField(prefs.nextPage)
        shownNext = prefs.nextPage
        pdfBox.addView(labeledField("Página em que a próxima sessão começa (avança sozinha)", etNext))

        pagesBox = LinearLayout(this)
        pagesBox.orientation = LinearLayout.VERTICAL
        etPerSession = numberField(prefs.pagesPerSession)
        pagesBox.addView(labeledField("Páginas por sessão", etPerSession))
        cbWhole = CheckBox(this)
        cbWhole.text = "Abrir o PDF inteiro de uma vez (ignora as páginas por sessão)"
        cbWhole.isChecked = prefs.wholePdf
        pagesBox.addView(cbWhole)
        etMinRead = numberField(prefs.minReadSeconds)
        pagesBox.addView(labeledField("Tempo mínimo de leitura, em segundos (até o botão de terminar liberar)", etMinRead))
        pdfBox.addView(pagesBox)

        cbHome = CheckBox(this)
        cbHome.text = "Ao terminar, voltar para a tela inicial"
        cbHome.isChecked = prefs.goHome
        pdfBox.addView(cbHome)
        col.addView(pdfBox)

        // app de estudo
        appBox = LinearLayout(this)
        appBox.orientation = LinearLayout.VERTICAL
        appBox.addView(heading("App de estudo"))
        tvStudyApp = TextView(this)
        tvStudyApp.textSize = 14f
        appBox.addView(tvStudyApp)
        appBox.addView(actionButton("Escolher app") { chooseStudyApp() })
        col.addView(appBox)

        // tempo de estudo (app de estudo, ou PDF por tempo)
        timeBox = LinearLayout(this)
        timeBox.orientation = LinearLayout.VERTICAL
        timeBox.addView(heading("Tempo de estudo"))
        etStudy = numberField(prefs.studyMinutes)
        timeBox.addView(
            labeledField(
                "Tempo de estudo por sessão, em minutos. O tempo não fica na tela: para ver quanto falta, é só abaixar a barra de notificações. Quando acabar, aparece a tela de descanso e toca um som.",
                etStudy
            )
        )
        col.addView(timeBox)

        // ligar
        col.addView(heading("Ligar"))
        swEnable = Switch(this)
        swEnable.text = "Ciclo de estudo ativo"
        swEnable.textSize = 16f
        col.addView(swEnable)
        col.addView(actionButton("Testar agora (abre a sessão já)") { testNow() })

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            prefs.mode = if (checkedId == rbApp.id) Prefs.MODE_APP else Prefs.MODE_PDF
            updateSections()
        }
        rgEnd.setOnCheckedChangeListener { _, checkedId ->
            prefs.endBy = if (checkedId == rbTime.id) Prefs.END_TIME else Prefs.END_PAGES
            updateSections()
        }
    }

    private fun heading(label: String): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 18f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(24), 0, dp(6))
        return t
    }

    private fun statusText(): TextView {
        val t = TextView(this)
        t.textSize = 13f
        return t
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setAllCaps(false)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun numberField(value: Int): EditText {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(value.toString())
        e.setSingleLine()
        return e
    }

    private fun labeledField(label: String, field: View): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(6), dp(8), 0)
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 13f
        box.addView(tv)
        box.addView(field)
        return box
    }

    private fun permRow(
        parent: LinearLayout,
        label: String,
        hint: String,
        status: TextView,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(4), 0, dp(4))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val t1 = TextView(this)
        t1.text = label
        t1.textSize = 15f
        t1.setTypeface(t1.typeface, Typeface.BOLD)
        val t2 = TextView(this)
        t2.text = hint
        t2.textSize = 12f
        col.addView(t1)
        col.addView(t2)
        col.addView(status)
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))

        val b = Button(this)
        b.text = "Abrir"
        b.setAllCaps(false)
        b.setOnClickListener { onClick() }
        row.addView(b)

        parent.addView(row)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ----------------------------------------------------------------- estado

    private fun setStatus(tv: TextView, ok: Boolean) {
        tv.text = if (ok) "✔ OK" else "✖ Pendente — toque em Abrir"
        tv.setTextColor(Color.parseColor(if (ok) "#2E7D32" else "#C62828"))
    }

    /** Mostra só as opções que fazem sentido para a combinação escolhida. */
    private fun updateSections() {
        val app = prefs.mode == Prefs.MODE_APP
        val timed = app || prefs.endBy == Prefs.END_TIME
        endBox.visibility = if (app) View.GONE else View.VISIBLE
        pdfBox.visibility = if (app) View.GONE else View.VISIBLE
        pagesBox.visibility = if (!app && !timed) View.VISIBLE else View.GONE
        appBox.visibility = if (app) View.VISIBLE else View.GONE
        timeBox.visibility = if (timed) View.VISIBLE else View.GONE
    }

    /** Atualiza o campo "próxima página" só se a pessoa não estiver editando. */
    private fun syncNextPageField() {
        val typed = etNext.text.toString().toIntOrNull()
        if (typed == null || typed == shownNext) {
            etNext.setText(prefs.nextPage.toString())
            shownNext = prefs.nextPage
        }
    }

    private fun appLabel(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun refreshStatus() {
        setStatus(tvOverlay, Settings.canDrawOverlays(this))
        setStatus(tvNotif, hasNotifPermission())
        setStatus(tvBattery, isIgnoringBattery())

        tvPdf.text = if (prefs.pdfFile.exists()) {
            "PDF atual: ${prefs.pdfName} (${prefs.pdfPages} páginas)"
        } else {
            "Nenhum PDF escolhido ainda."
        }
        tvStudyApp.text = if (prefs.studyApp.isEmpty()) {
            "Nenhum app escolhido ainda."
        } else {
            "App de estudo: ${appLabel(prefs.studyApp)}"
        }

        syncNextPageField()
        updateSections()

        swEnable.setOnCheckedChangeListener(null)
        swEnable.isChecked = prefs.enabled
        swEnable.setOnCheckedChangeListener(onSwitch)
    }

    private fun hasNotifPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isIgnoringBattery(): Boolean {
        val p = getSystemService(Context.POWER_SERVICE) as PowerManager
        return p.isIgnoringBatteryOptimizations(packageName)
    }

    private fun saveFields() {
        prefs.breakMinutes = (etBreak.text.toString().toIntOrNull() ?: 10).coerceIn(1, 600)
        prefs.studyMinutes = (etStudy.text.toString().toIntOrNull() ?: 10).coerceIn(1, 600)
        prefs.pagesPerSession = (etPerSession.text.toString().toIntOrNull() ?: 1).coerceIn(1, 100)
        prefs.minReadSeconds = (etMinRead.text.toString().toIntOrNull() ?: 30).coerceIn(0, 600)
        prefs.wholePdf = cbWhole.isChecked
        val msg = etMessage.text.toString().trim()
        prefs.message = if (msg.isEmpty()) "Hora de estudar! 📚" else msg
        prefs.goHome = cbHome.isChecked
        val rest = etRestMessage.text.toString().trim()
        prefs.restMessage = if (rest.isEmpty()) "Hora do descanso ☕" else rest
        prefs.sound = cbSound.isChecked

        // só grava a "próxima página" se a pessoa mexeu no campo (senão sobrescreveria o avanço automático)
        val typed = etNext.text.toString().toIntOrNull()
        if (typed != null && typed != shownNext) {
            prefs.nextPage = typed.coerceAtLeast(1)
            shownNext = prefs.nextPage
        }
    }

    // ------------------------------------------------------------- permissões

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            toast("Abra Configurações > Apps > Acesso especial > Sobrepor a outros apps")
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        } else {
            toast("As notificações já estão liberadas.")
        }
    }

    private fun askBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                toast("Abra Configurações > Bateria e desative a otimização para este app")
            }
        }
    }

    // -------------------------------------------------------------- liga/desliga

    private fun rejectEnable(msg: String) {
        toast(msg)
        prefs.enabled = false
        refreshStatus()
    }

    private fun toggleMonitoring(on: Boolean) {
        if (on) {
            saveFields()
            if (!Settings.canDrawOverlays(this)) {
                rejectEnable("Conceda primeiro a permissão de aparecer sobre outros apps.")
                return
            }
            if (prefs.mode == Prefs.MODE_PDF && !prefs.pdfFile.exists()) {
                rejectEnable("Escolha um PDF primeiro.")
                return
            }
            if (prefs.mode == Prefs.MODE_APP && prefs.studyApp.isEmpty()) {
                rejectEnable("Escolha o app de estudo primeiro.")
                return
            }
            prefs.startCycle()
            prefs.enabled = true
            MonitorService.start(this)
            toast("Ciclo iniciado! Primeiro vem o descanso de ${prefs.breakMinutes} min.")
        } else {
            prefs.enabled = false
            MonitorService.stop(this)
            toast("Ciclo desligado")
        }
    }

    private fun testNow() {
        saveFields()
        if (!Settings.canDrawOverlays(this)) {
            toast("Conceda antes a permissão de aparecer sobre outros apps.")
            return
        }
        if (prefs.mode == Prefs.MODE_PDF && !prefs.pdfFile.exists()) {
            toast("Escolha um PDF primeiro.")
            return
        }
        if (prefs.mode == Prefs.MODE_APP && prefs.studyApp.isEmpty()) {
            toast("Escolha o app de estudo primeiro.")
            return
        }
        MonitorService.test(this)
    }

    // -------------------------------------------------------------------- PDF

    private fun pickPdf() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        startActivityForResult(i, REQ_PDF)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PDF && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importPdf(uri)
        }
    }

    private fun importPdf(uri: Uri) {
        toast("Importando PDF…")
        Thread {
            try {
                val tmp = File(filesDir, "estudo.tmp")
                val input = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("sem acesso ao arquivo")
                input.use { src ->
                    tmp.outputStream().use { out -> src.copyTo(out) }
                }
                val pages = countPages(tmp)
                if (pages <= 0) throw IllegalStateException("o PDF não tem páginas")

                val dest = prefs.pdfFile
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) throw IllegalStateException("falha ao salvar o arquivo")

                val name = queryName(uri) ?: "documento.pdf"
                runOnUiThread {
                    prefs.pdfName = name
                    prefs.pdfPages = pages
                    prefs.nextPage = 1
                    etNext.setText("1")
                    shownNext = 1
                    refreshStatus()
                    toast("PDF carregado: $pages páginas. Ele começa pela página 1.")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Não consegui usar esse PDF: ${e.message}") }
            }
        }.start()
    }

    private fun countPages(f: File): Int {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
        try {
            return r.pageCount
        } finally {
            r.close()
        }
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ app de estudo

    private fun chooseStudyApp() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { Pair(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val labels = apps.map { it.second }.toTypedArray()
        var chosen = apps.indexOfFirst { it.first == prefs.studyApp }

        AlertDialog.Builder(this)
            .setTitle("App de estudo")
            .setSingleChoiceItems(labels, chosen) { _, which -> chosen = which }
            .setPositiveButton("OK") { _, _ ->
                if (chosen >= 0) {
                    prefs.studyApp = apps[chosen].first
                    refreshStatus()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
