package ru.zhti.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.Manifest
import android.os.Build
import android.webkit.JavascriptInterface
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
        webView.addJavascriptInterface(NotificationPermissionBridge(this), "AndroidNotifications")
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
            // Give the local page the Supabase HTTPS origin so REST calls are same-origin
            // instead of being blocked by WebView's file:// (null) origin CORS policy.
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


class NotificationPermissionBridge(private val activity: Activity) {
    @JavascriptInterface
    fun requestPermission() {
        activity.runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    @JavascriptInterface
    fun showChallengeNotification(challenger: String, game: String, battleType: String) {
        activity.runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return@runOnUiThread
            }
            val channelId = "zhti_battles"
            val manager = activity.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Вызовы на битву", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(channel)
            }
            val intent = Intent(activity, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                activity, 1002, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = "$challenger вызывает тебя · $game · $battleType"
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(activity, channelId)
            } else {
                Notification.Builder(activity)
            }
            val notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Тебе кинули вызов!")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }
}
