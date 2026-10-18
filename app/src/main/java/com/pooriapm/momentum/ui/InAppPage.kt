package com.pooriapm.momentum.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A website page shown inside the app (safety, privacy, terms…): full screen, with a close button
 * and a progress line. Links to the same site stay here; anything else goes to the system. Pages
 * come from the HTTP cache when they are fresh, and the WebView is destroyed on close.
 */
@Composable
fun InAppPage(url: String, title: String, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var progress by remember { mutableFloatStateOf(0f) }
        var loading by remember { mutableStateOf(true) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        val background = MaterialTheme.colorScheme.background
        val dark = isSystemInDarkTheme()
        // The dialog has its own window: give it status-bar icons that read on our background.
        val view = LocalView.current
        LaunchedEffect(dark) {
            (view.parent as? DialogWindowProvider)?.window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark
            }
        }
        BackHandler { webView?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }

        Column(Modifier.fillMaxSize().background(background).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "بستن") }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (loading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f)) {
                AndroidView(
                    factory = { context -> createWebView(context, url, background.toArgb(), { progress = it }, { loading = it }).also { webView = it } },
                    modifier = Modifier.fillMaxSize(),
                )
                // Until the first page is drawn: a native placeholder instead of a blank white page.
                if (loading && progress < 0.6f) {
                    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                webView?.apply {
                    stopLoading()
                    destroy()
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    url: String,
    background: Int,
    onProgress: (Float) -> Unit,
    onLoading: (Boolean) -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(background)
    settings.apply {
        javaScriptEnabled = true // the website is a React app
        domStorageEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT // honours HTTP caching: repeat visits load from cache
        setSupportZoom(false)
        allowFileAccess = false
        allowContentAccess = false
        mediaPlaybackRequiresUserGesture = true
    }
    val host = Uri.parse(url).host
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.host == host) return false
            // Other sites (mail links, payment, social) open outside, if anything can open them.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = onLoading(true)

        override fun onPageCommitVisible(view: WebView, url: String?) = view.hideSiteChrome()

        override fun onPageFinished(view: WebView, url: String?) {
            view.hideSiteChrome()
            onLoading(false)
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress / 100f)
    }
    loadUrl(url)
}

/**
 * The website's own header, menu and footer duplicate the app's chrome; hide them so only the
 * page content shows. Idempotent, so it can run on every page event.
 */
private fun WebView.hideSiteChrome() {
    evaluateJavascript(
        """
        (function(){
          if (document.getElementById('momentum-app-embed')) return;
          var s = document.createElement('style');
          s.id = 'momentum-app-embed';
          s.textContent = '.public-header-wrap,.public-menu-backdrop,.public-footer{display:none!important}.legal-doc main{padding-top:1rem!important}';
          (document.head || document.documentElement).appendChild(s);
        })();
        """.trimIndent(),
        null,
    )
}
