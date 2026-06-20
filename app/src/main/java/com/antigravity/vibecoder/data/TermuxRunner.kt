package com.antigravity.vibecoder.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object TermuxRunner {

    private const val ACTION_RESULT = "com.antigravity.vibecoder.TERMUX_RESULT"
    private const val EXTRA_REQUEST_ID = "request_id"

    private val activeCallbacks = ConcurrentHashMap<String, (TermuxResult) -> Unit>()

    data class TermuxResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val error: String?
    )

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != ACTION_RESULT) return
            val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return

            val resultBundle = intent.getBundleExtra("com.termux.execute.plugin_result_bundle")
                ?: intent.getBundleExtra("EXTRA_PLUGIN_RESULT_BUNDLE")

            val stdout = resultBundle?.getString("stdout") ?: intent.getStringExtra("stdout") ?: ""
            val stderr = resultBundle?.getString("stderr") ?: intent.getStringExtra("stderr") ?: ""
            val exitCode = resultBundle?.getInt("exitCode", -1) ?: intent.getIntExtra("exitCode", -1)
            val error = resultBundle?.getString("err") ?: intent.getStringExtra("err")

            val callback = activeCallbacks.remove(requestId)
            callback?.invoke(TermuxResult(stdout, stderr, exitCode, error))
        }
    }

    // C-2 FIX: AtomicBoolean is thread-safe — prevents race between coroutine and onDestroy threads
    private val isReceiverRegistered = AtomicBoolean(false)

    fun registerReceiver(context: Context) {
        // compareAndSet: only registers if currently false, atomically flips to true
        if (!isReceiverRegistered.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val filter = IntentFilter(ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(resultReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(resultReceiver, filter)
        }
    }

    fun unregisterReceiver(context: Context) {
        // compareAndSet: only unregisters if currently true, atomically flips to false
        if (!isReceiverRegistered.compareAndSet(true, false)) return
        // B-2 FIX: Clear stale callbacks from previous lifecycle to prevent phantom invocations
        activeCallbacks.clear()
        try {
            context.applicationContext.unregisterReceiver(resultReceiver)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
    }

    suspend fun executeCommand(
        context: Context,
        command: String,
        workingDir: String = "/data/data/com.termux/files/home"
    ): TermuxResult {
        return try {
            withTimeout(60_000L) {
                suspendCancellableCoroutine { continuation ->
                    registerReceiver(context)
                    val requestId = UUID.randomUUID().toString()

                    activeCallbacks[requestId] = { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }

                    val resultIntent = Intent(ACTION_RESULT).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_REQUEST_ID, requestId)
                    }

                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    else PendingIntent.FLAG_UPDATE_CURRENT

                    val pendingIntent = PendingIntent.getBroadcast(
                        context, requestId.hashCode(), resultIntent, flags
                    )

                    val runIntent = Intent().apply {
                        setClassName("com.termux", "com.termux.app.RunCommandService")
                        action = "com.termux.RUN_COMMAND"
                        putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                        putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                        putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDir)
                        putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                        putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
                    }

                    try {
                        context.startService(runIntent)
                    } catch (e: Exception) {
                        activeCallbacks.remove(requestId)
                        if (continuation.isActive) {
                            continuation.resume(
                                TermuxResult("", "", -1, "Failed to start Termux Service: ${e.message}")
                            )
                        }
                    }

                    continuation.invokeOnCancellation { activeCallbacks.remove(requestId) }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            TermuxResult("", "", -1, "Command timed out after 60 seconds. Is Termux running?")
        }
    }
}
