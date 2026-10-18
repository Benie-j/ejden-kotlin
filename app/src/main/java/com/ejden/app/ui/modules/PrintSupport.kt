package com.ejden.app.ui.modules

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

// Le WebView doit rester en mémoire jusqu'à la fin de l'impression.
private var printingWebView: WebView? = null

private fun Context.findHostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Ouvre l'impression d'Android pour une page HTML : on peut imprimer
 * ou choisir « Enregistrer au format PDF ». Rend false si c'est impossible.
 */
internal fun printHtml(context: Context, html: String, jobName: String): Boolean {
    val activity = context.findHostActivity() ?: return false

    return try {
        val webView = WebView(activity)
        webView.settings.javaScriptEnabled = false

        var started = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (started || view == null) return
                started = true

                val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500))
                    .build()
                manager.print(jobName, view.createPrintDocumentAdapter(jobName), attributes)
            }
        }

        printingWebView = webView
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        true
    } catch (e: Exception) {
        false
    }
}

/** Partage un texte (WhatsApp, SMS, e-mail…). Rend false si c'est impossible. */
internal fun shareDocumentText(context: Context, title: String, text: String): Boolean = try {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, title)
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
    true
} catch (e: Exception) {
    false
}
