package com.antigravity.vibecoder.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

object TermuxRunner {

    private const val ACTION_RESULT = "com.antigravity.vibecoder.TERMUX_RESULT"
    private const val EXTRA_REQUEST_ID = "request_id"

    // Maps requestId to active continuation
    private val activeCallbacks = mutableMapOf<String, (TermuxResult) -> Unit>()

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
            
            // Termux returns results in a plugin bundle
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

    private var isReceiverRegistered = false

    fun registerReceiver(context: Context) {
        if (isReceiverRegistered) return
        val filter = IntentFilter(ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(resultReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(resultReceiver, filter)
        }
        isReceiverRegistered = true
    }

    fun unregisterReceiver(context: Context) {
        if (!isReceiverRegistered) return
        context.unregisterReceiver(resultReceiver)
        isReceiverRegistered = false
    }

    suspend fun executeCommand(
        context: Context,
        command: String,
        workingDir: String = "/data/data/com.termux/files/home"
    ): TermuxResult = suspendCancellableCoroutine { continuation ->
        registerReceiver(context)
        val requestId = UUID.randomUUID().toString()

        activeCallbacks[requestId] = { result ->
            continuation.resume(result)
        }

        // Create the result PendingIntent
        val resultIntent = Intent(ACTION_RESULT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_REQUEST_ID, requestId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            resultIntent,
            flags
        )

        // Create the RUN_COMMAND execution Intent
        val runIntent = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            
            // Execute login terminal session (or bash directly)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/login")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
        }

        try {
            context.startService(runIntent)
        } catch (e: Exception) {
            activeCallbacks.remove(requestId)
            continuation.resume(TermuxResult("", "", -1, "Failed to start Termux Service: ${e.message}"))
        }

        continuation.invokeOnCancellation {
            activeCallbacks.remove(requestId)
        }
    }
}
