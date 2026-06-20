package com.antigravity.vibecoder.ui.view

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.antigravity.vibecoder.ui.theme.*

@Composable
fun PreviewView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(url.ifEmpty { "about:blank" }) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // B-1 FIX: Sync currentUrl and WebView when the incoming url prop changes
    LaunchedEffect(url) {
        if (url.isNotEmpty() && url != currentUrl) {
            currentUrl = url
            webViewRef?.loadUrl(url)
        }
    }

    // C-4 FIX: Destroy WebView when composable leaves composition to prevent OOM
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                clearHistory()
                destroy()
            }
            webViewRef = null
        }
    }

    // B-3 FIX: Pause/Resume WebView correctly when the app is backgrounded
    // This stops audio/video and heavy JS loops from running in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .border(1.dp, DarkBorder)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ARTIFACT PREVIEW",
                color = TerminalCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TerminalGreen, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = {
                        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                            } catch (e: Exception) { /* no browser */ }
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser", tint = TerminalAmber, modifier = Modifier.size(16.dp))
                }
            }
        }

        // URL Bar
        OutlinedTextField(
            value = currentUrl,
            onValueChange = { currentUrl = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).height(48.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalWhite),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreenDim, unfocusedBorderColor = DarkBorder),
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { webViewRef?.loadUrl(currentUrl) }
            )
        )

        // WebView
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, DarkBorder, RoundedCornerShape(4.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        // S-2 FIX: Restrict file system and content provider access
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString() ?: return true
                                // S-2 FIX: Only allow http/https — block file://, content://, javascript:
                                return !(reqUrl.startsWith("http://") || reqUrl.startsWith("https://"))
                            }
                        }
                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                update = { view ->
                    if (view.url != currentUrl && currentUrl.isNotEmpty()) {
                        view.loadUrl(currentUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
