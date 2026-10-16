package it.azulsistemi.aacrelay

import android.content.Context
import android.net.Uri

/** Indirizzo della pagina AAC, salvato sul telefono: il token non finisce nel sorgente. */
object Prefs {
    private const val FILE = "aacrelay"
    private const val KEY_PAGE_URL = "pageUrl"

    fun pageUrl(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PAGE_URL, "") ?: ""

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
