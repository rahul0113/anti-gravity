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
import androidx.compose.ui.graphics.Brush
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

    // Liquid Glass Gradient Background
    val glassGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E293B), // Deep Slate
            Color(0xFF3B82F6).copy(alpha = 0.3f), // Soft Blue
            Color(0xFF8B5CF6).copy(alpha = 0.2f), // Purple Tint
            Color(0xFF0F172A)  // Very Dark Blue
        )
    )

    // Glassmorphism modifier
    val glassModifier = Modifier
        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(glassGradient)
            .padding(12.dp)
    ) {
        // Top Bar - Liquid Glass Style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(glassModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Preview",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(36.dp).background(Color.White.copy(alpha=0.1f), RoundedCornerShape(18.dp))) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                            } catch (e: Exception) { /* no browser */ }
                        }
                    },
                    modifier = Modifier.size(36.dp).background(Color.White.copy(alpha=0.1f), RoundedCornerShape(18.dp))
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))

        // URL Bar - Glass
        OutlinedTextField(
            value = currentUrl,
            onValueChange = { currentUrl = it },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha=0.5f), 
                unfocusedBorderColor = Color.White.copy(alpha=0.2f),
                focusedContainerColor = Color.White.copy(alpha=0.05f),
                unfocusedContainerColor = Color.White.copy(alpha=0.05f)
            ),
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { webViewRef?.loadUrl(currentUrl) }
            )
        )
        
        Spacer(Modifier.height(12.dp))

        // WebView - Glass Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(glassModifier)
                .padding(2.dp) // inner padding so WebView doesn't overlap the border
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
                        post { webViewRef = this@apply }
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
