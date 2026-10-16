package it.azulsistemi.aacrelay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Mostra la pagina AAC a schermo intero e avvia il servizio che suona il beep.
 * La pagina è quella di AAC, invariata: l'app non duplica nessuna logica.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var errorShown = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // Solo la pagina principale: un errore su una risorsa secondaria non conta
                if (request.isForMainFrame) showUnreachable()
            }
        }
        setContentView(buildLayout())

        askNotificationPermission()
        askBatteryExemption()

        open(Prefs.pageUrl(this))
        if (intent.getBooleanExtra(EXTRA_ASK_URL, false)) askUrl()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Azione "Indirizzo" della notifica con l'app già aperta
        if (intent.getBooleanExtra(EXTRA_ASK_URL, false)) askUrl()
    }

    companion object {
        /** Extra dell'intent che apre direttamente la correzione dell'indirizzo. */
        const val EXTRA_ASK_URL = "askUrl"

        /** Stesso grigio dei pannelli della pagina AAC. */
        private const val BAR_COLOR = 0xFF2B2B2B.toInt()
    }

    /** Barra con Indirizzo ed Esci sopra la pagina, il tutto fuori dalle barre di sistema. */
    private fun buildLayout(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BAR_COLOR)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.app_name)
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(32, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(barButton(R.string.change_url) { askUrl() })
            addView(barButton(R.string.exit) { exit() })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BAR_COLOR)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            // Da Android 15 l'app si estende sotto barra di stato e di navigazione: il margine
            // lo dà l'altezza delle barre, e della tastiera quando è aperta. Prima no, e il
            // margine sarebbe doppio.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                setOnApplyWindowInsetsListener { v, insets ->
                    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                    v.setPadding(b.left, b.top, b.right, b.bottom)
                    insets
                }
            }
        }
    }

    private fun barButton(textId: Int, onClick: () -> Unit): Button =
        Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(textId)
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }

    /** Ferma la sorveglianza e chiude l'app: niente più beep finché non la si riapre. */
    private fun exit() {
        stopService(Intent(this, RelayWatchService::class.java))
        finishAndRemoveTask()
    }

    private fun open(url: String) {
        errorShown = false
        webView.loadUrl(url)
        val intent = Intent(this, RelayWatchService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    /** Chiede l'indirizzo completo della pagina, token compreso. */
    private fun askUrl() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(Prefs.pageUrl(this@MainActivity))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.url_title)
            .setMessage(R.string.url_message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (Prefs.statusUrl(url) == null) { askUrl(); return@setPositiveButton }
                // Il servizio si accorge del cambio al prossimo avvio in open()
                Prefs.setPageUrl(this, url)
                open(url)
            }
            .setNeutralButton(R.string.default_url) { _, _ ->
                Prefs.setPageUrl(this, "")
                open(Prefs.pageUrl(this))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUnreachable() {
        if (errorShown) return
        errorShown = true
        AlertDialog.Builder(this)
            .setTitle(R.string.unreachable_title)
            .setMessage(Prefs.pageUrl(this))
            .setPositiveButton(R.string.retry) { _, _ -> open(Prefs.pageUrl(this)) }
            .setNegativeButton(R.string.change_url) { _, _ -> askUrl() }
            .show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** Senza esenzione dal risparmio energetico Android sospende la rete in standby. */
    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        val power = getSystemService(PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Tornando sull'app la pagina deve essere aggiornata subito
        if (::webView.isInitialized && Prefs.pageUrl(this).isNotEmpty()) webView.reload()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Indietro manda l'app in secondo piano senza chiuderla: il servizio resta attivo
        moveTaskToBack(true)
    }
}
