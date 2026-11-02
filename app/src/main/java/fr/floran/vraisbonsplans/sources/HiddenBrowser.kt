package fr.floran.vraisbonsplans.sources

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Invisible WebView for the sites whose anti-bot rejects plain HTTP clients (idealo, Ledenicheur):
 * a real browser engine passes. The page is loaded, then [script] runs inside it and must call
 * `Bridge.onResult(string)` exactly once.
 */
object HiddenBrowser {

    private const val TAG = "HiddenBrowser"
    private const val PAGE_SETTLE_DELAY_MILLIS = 3_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun runOnPage(context: Context, url: String, script: String, timeoutMillis: Long): String =
        withContext(Dispatchers.Main) {
            withTimeout(timeoutMillis) { loadAndRun(context.applicationContext, url, script) }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadAndRun(context: Context, url: String, script: String): String =
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context)
            // Anti-bots reload the page once or twice; only the script of the latest load may run.
            var loadGeneration = 0

            fun destroyLater() = mainHandler.post { webView.destroy() }

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun onResult(result: String) {
                    if (!continuation.isActive) return
                    continuation.resume(result)
                    destroyLater()
                }
            }, "Bridge")

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.w(TAG, "console error on $url: ${message.message()} @${message.lineNumber()}")
                    }
                    return true
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    val generation = ++loadGeneration
                    // View.postDelayed would never run: an off-screen WebView is never attached to a window.
                    mainHandler.postDelayed({
                        if (generation == loadGeneration && continuation.isActive) view.evaluateJavascript(script, null)
                    }, PAGE_SETTLE_DELAY_MILLIS)
                }
            }

            continuation.invokeOnCancellation { destroyLater() }
            try {
                webView.loadUrl(url)
            } catch (error: Exception) {
                continuation.resumeWithException(error)
                destroyLater()
            }
        }
}
