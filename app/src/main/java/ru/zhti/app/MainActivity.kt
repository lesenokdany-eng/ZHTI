package ru.zhti.app

import android.app.Activity
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                android.util.Log.e("ZHTI-WebView", "${message.message()} at ${message.sourceId()}:${message.lineNumber()}")
                return true
            }
        }
        setContentView(webView)
        if (savedInstanceState == null) {
            val html = assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
            webView.loadDataWithBaseURL(
                "https://jylcbiznpnaezxeumcsm.supabase.co/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}

