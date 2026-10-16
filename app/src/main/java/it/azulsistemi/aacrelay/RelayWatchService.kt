package it.azulsistemi.aacrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Servizio in primo piano: interroga `/relay/status` di AAC e suona un beep quando compare un
 * passaggio nuovo da confermare. Gira anche a schermo spento, che è il motivo dell'app.
 *
 * Il beep suona una volta per ticket: lo stesso passaggio ancora aperto non risuona a ogni
 * interrogazione, uno nuovo sì.
 */
class RelayWatchService : Service() {

    private companion object {
        const val CHANNEL_ID = "watch"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "it.azulsistemi.aacrelay.STOP"
        const val POLL_MS = 3000L
        const val TIMEOUT_MS = 2500
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var tone: ToneGenerator? = null
    private var statusUrl: String? = null
    private var lastTicket = 0L
    private var lastLine = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        thread = HandlerThread("relay-watch").apply { start() }
        handler = Handler(thread.looper)
        tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)

        // Senza questi lock, in standby la CPU e il Wi-Fi si addormentano e il polling si ferma
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AacRelay:watch").apply { acquire() }
        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AacRelay:watch").apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(getString(R.string.status_starting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val url = Prefs.statusUrl(Prefs.pageUrl(this))
        if (url != statusUrl) {
            statusUrl = url
            handler.removeCallbacksAndMessages(null)
            handler.post(::poll)
        }
        return START_STICKY
    }

    private fun poll() {
        val url = statusUrl ?: return
        val line = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.useCaches = false
            try {
                when (conn.responseCode) {
                    200 -> readStatus(JSONObject(conn.inputStream.bufferedReader().readText()))
                    401 -> getString(R.string.status_bad_token)
                    else -> getString(R.string.status_http_error, conn.responseCode)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            getString(R.string.status_unreachable)
        }
        if (line != lastLine) {
            lastLine = line
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(line))
        }
        handler.postDelayed(::poll, POLL_MS)
    }

    /** Legge lo stato, suona se il passaggio è nuovo, e restituisce la riga per la notifica. */
    private fun readStatus(status: JSONObject): String {
        if (!status.optBoolean("isOpen")) return getString(R.string.status_idle)

        val ticket = status.optLong("ticket")
        if (ticket != 0L && ticket != lastTicket) {
            lastTicket = ticket
            beep()
        }
        return getString(
            R.string.status_waiting,
            status.optString("sourceTitle"),
            status.optString("targetTitle")
        )
    }

    private fun beep() {
        val t = tone ?: return
        // Due beep ravvicinati: si distinguono da una notifica qualsiasi
        t.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        handler.postDelayed({ t.startTone(ToneGenerator.TONE_PROP_BEEP2, 300) }, 500)

        val effect = VibrationEffect.createWaveform(longArrayOf(0, 200, 150, 200), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(effect)
        } else {
            getSystemService(Vibrator::class.java).vibrate(effect)
        }
    }

    private fun createChannel() {
        // Canale silenzioso: la notifica fissa non deve suonare, il beep lo fa ToneGenerator
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val askUrl = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ASK_URL, true),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RelayWatchService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.change_url), askUrl).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        tone?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
