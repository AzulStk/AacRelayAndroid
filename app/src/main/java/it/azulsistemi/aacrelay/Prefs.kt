package it.azulsistemi.aacrelay

import android.content.Context
import android.net.Uri

/** Indirizzo della pagina AAC: quello predefinito, oppure quello corretto a mano sul telefono. */
object Prefs {
    private const val FILE = "aacrelay"
    private const val KEY_PAGE_URL = "pageUrl"

    /** Indirizzo usato finché non se ne salva un altro. */
    const val DEFAULT_PAGE_URL = "http://192.168.28.10:8765/?t=5f3e5f517ec55717d1eeb6f4"

    fun pageUrl(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PAGE_URL, null)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_PAGE_URL

    fun setPageUrl(context: Context, url: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_PAGE_URL, url.trim()).apply()
    }

    /** Da `http://host:porta/?t=TOKEN` ricava `http://host:porta/relay/status?t=TOKEN`. */
    fun statusUrl(pageUrl: String): String? {
        val page = Uri.parse(pageUrl)
        if (page.scheme.isNullOrEmpty() || page.authority.isNullOrEmpty()) return null
        val token = page.getQueryParameter("t") ?: ""
        return Uri.Builder()
            .scheme(page.scheme)
            .encodedAuthority(page.encodedAuthority)
            .path("/relay/status")
            .appendQueryParameter("t", token)
            .build()
            .toString()
    }
}
