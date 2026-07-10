package com.example.detectionapplication

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Displays an MJPEG stream from the Django backend inside a WebView.
 *
 * Why WebView? Android's image/networking libraries don't support
 * multipart/x-mixed-replace (the protocol MJPEG uses). WebView handles
 * it natively through the system HTTP stack — no extra dependencies needed.
 *
 * @param streamUrl  Full URL to Django's stream endpoint.
 *                   e.g. "http://10.0.2.2:8000/api/stream/"
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MjpegStreamView(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // If the URL changes while composable is alive, reload
    LaunchedEffect(streamUrl) {
        webViewRef?.loadUrl(streamUrl)
    }

    // When Stop is pressed this composable leaves composition — destroy cleanly
    // so the HTTP connection is closed and the Django stream generator exits too
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled            = true
                    loadWithOverviewMode         = true
                    useWideViewPort              = true
                    cacheMode                    = WebSettings.LOAD_NO_CACHE
                    mediaPlaybackRequiresUserGesture = false
                }
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl(streamUrl)
                webViewRef = this
            }
        },
        update = { view ->
            if (view.url != streamUrl) view.loadUrl(streamUrl)
        }
    )
}