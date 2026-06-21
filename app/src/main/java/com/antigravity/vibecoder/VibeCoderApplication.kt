package com.antigravity.vibecoder

import android.app.Application
import android.content.Context

/**
 * Custom Application class that installs a global uncaught exception handler.
 * When the app crashes, the full stack trace is saved to SharedPreferences.
 * On the next launch, MainActivity reads it and displays it in the terminal view
 * so the user (or developer) can see EXACTLY what crashed.
 */
class VibeCoderApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler(this)
    }

    companion object {
        private const val PREFS_NAME = "vibecoder_crash_log"
        private const val KEY_CRASH = "last_crash"

        private fun installCrashHandler(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val trace = buildString {
                        appendLine("=== ANTI-GRAVITY CRASH REPORT ===")
                        appendLine("Thread: ${thread.name}")
                        appendLine("Exception: ${throwable.javaClass.name}")
                        appendLine("Message: ${throwable.message}")
                        appendLine()
                        appendLine("--- Stack Trace ---")
                        for (element in throwable.stackTrace) {
                            appendLine("  at $element")
                        }
                        var cause = throwable.cause
                        while (cause != null) {
                            appendLine()
                            appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
                            for (element in cause.stackTrace) {
                                appendLine("  at $element")
                            }
                            cause = cause.cause
                        }
                    }
                    context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_CRASH, trace)
                        .commit() // commit() not apply() — must be synchronous before process dies
                } catch (_: Throwable) {
                    // If even crash logging fails, don't make things worse
                }
                // Forward to the default handler so Android shows the standard crash dialog
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        /**
         * Returns the last crash log (if any) and clears it.
         */
        fun consumeLastCrashLog(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val log = prefs.getString(KEY_CRASH, null)
            if (log != null) {
                prefs.edit().remove(KEY_CRASH).apply()
            }
            return log
        }
    }
}
