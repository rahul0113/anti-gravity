package com.antigravity.vibecoder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TerminalGreen,
    secondary = TerminalCyan,
    tertiary = TerminalAmber,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onTertiary = DarkBackground,
    onBackground = TerminalWhite,
    onSurface = TerminalWhite
)

@Composable
fun AntiGravityVibeCoderTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            try {
                var context = view.context
                while (context is android.content.ContextWrapper && context !is Activity) {
                    context = context.baseContext
                }
                if (context is Activity) {
                    val window = context.window
                    // Only apply status bar coloring on API 21+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        window.statusBarColor = colorScheme.background.toArgb()
                        window.navigationBarColor = colorScheme.background.toArgb()
                    }
                    // Only use WindowCompat insets on API 23+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                    }
                }
            } catch (e: Exception) {
                // Silently ignore any theme application failures to prevent crash
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
