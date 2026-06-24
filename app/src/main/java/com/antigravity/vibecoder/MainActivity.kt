package com.antigravity.vibecoder

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.data.AgentExecutor
import com.antigravity.vibecoder.data.TermuxRunner
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import com.antigravity.vibecoder.ui.theme.*
import com.antigravity.vibecoder.ui.view.EditorView
import com.antigravity.vibecoder.ui.view.SettingsView
import com.antigravity.vibecoder.ui.view.TerminalView
import kotlinx.coroutines.launch

enum class Screen { CHAT, EDITOR, SETTINGS }

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var agentExecutor: AgentExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            sharedPreferences = getSharedPreferences("vibecoder_prefs", Context.MODE_PRIVATE)
            agentExecutor = AgentExecutor(applicationContext)

            val previousCrashLog = VibeCoderApplication.consumeLastCrashLog(this)
            if (previousCrashLog != null) showCrashDialog(previousCrashLog)

            setContent {
                AntiGravityVibeCoderTheme {
                    fun SharedPreferences.safeGetString(key: String, defValue: String): String {
                        return try { getString(key, defValue) ?: defValue } catch (e: Exception) { defValue }
                    }
                    fun SharedPreferences.safeGetInt(key: String, defValue: Int): Int {
                        return try { getInt(key, defValue) } catch (e: Exception) { defValue }
                    }

                    LaunchedEffect(Unit) {
                        if (previousCrashLog != null) agentExecutor.injectCrashLog(previousCrashLog)
                    }

                    // State
                    var apiKey by remember { mutableStateOf(sharedPreferences.safeGetString("api_key", "")) }
                    var baseUrl by remember { mutableStateOf(sharedPreferences.safeGetString("base_url", com.antigravity.vibecoder.data.Provider.OPENAI.defaultBaseUrl)) }
                    var modelName by remember { mutableStateOf(sharedPreferences.safeGetString("model_name", com.antigravity.vibecoder.data.Provider.OPENAI.defaultModel)) }
                    var executionModeStr by remember { mutableStateOf(sharedPreferences.safeGetString("execution_mode", ExecutionMode.OPENCLAUDE.name)) }
                    val executionMode = try { ExecutionMode.valueOf(executionModeStr) } catch (e: Exception) { ExecutionMode.OPENCLAUDE }
                    var sshHost by remember { mutableStateOf(sharedPreferences.safeGetString("ssh_host", "127.0.0.1")) }
                    var sshPort by remember { mutableIntStateOf(sharedPreferences.safeGetInt("ssh_port", 8022)) }
                    var sshUser by remember { mutableStateOf(sharedPreferences.safeGetString("ssh_user", "android")) }
                    var sshPass by remember { mutableStateOf(sharedPreferences.safeGetString("ssh_pass", "")) }
                    var sshWorkspace by remember { mutableStateOf(sharedPreferences.safeGetString("ssh_workspace", "/data/data/com.termux/files/home")) }
                    val grpcPort by remember { mutableIntStateOf(sharedPreferences.safeGetInt("grpc_port", 50051)) }

                    val config = remember(executionMode, sshHost, sshPort, sshUser, sshPass, sshWorkspace, grpcPort) {
                        ConnectionConfig(executionMode = executionMode, host = sshHost, port = sshPort, grpcPort = grpcPort, user = sshUser, passwordKey = sshPass, workspacePath = sshWorkspace)
                    }

                    var currentScreen by remember { mutableStateOf(Screen.CHAT) }

                    // Portrait on chat and settings
                    LaunchedEffect(currentScreen) {
                        requestedOrientation = when (currentScreen) {
                            Screen.CHAT, Screen.SETTINGS -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            Screen.EDITOR -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }

                    val messages by agentExecutor.messages.collectAsState()
                    val isProcessing by agentExecutor.isProcessing.collectAsState()
                    val coroutineScope = rememberCoroutineScope()

                    val saveApiKey: (String) -> Unit = { apiKey = it; sharedPreferences.edit().putString("api_key", it).apply() }
                    val saveBaseUrl: (String) -> Unit = { baseUrl = it; sharedPreferences.edit().putString("base_url", it).apply() }
                    val saveModelName: (String) -> Unit = { modelName = it; sharedPreferences.edit().putString("model_name", it).apply() }
                    val saveConfig: (ConnectionConfig) -> Unit = { c ->
                        executionModeStr = c.executionMode.name; sshHost = c.host; sshPort = c.port; sshUser = c.user; sshPass = c.passwordKey; sshWorkspace = c.workspacePath
                        sharedPreferences.edit().apply {
                            putString("execution_mode", c.executionMode.name); putString("ssh_host", c.host); putInt("ssh_port", c.port); putString("ssh_user", c.user); putString("ssh_pass", c.passwordKey); putString("ssh_workspace", c.workspacePath)
                        }.apply()
                    }

                    val sendPrompt: (String) -> Unit = { prompt ->
                        if (apiKey.isEmpty() && config.executionMode != ExecutionMode.SANDBOX) {
                            coroutineScope.launch { agentExecutor.executeUserPrompt("Set your API key in Settings first.", "", "", "", config) }
                        } else {
                            coroutineScope.launch { agentExecutor.executeUserPrompt(prompt, apiKey, baseUrl, modelName, config) }
                        }
                    }

                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.CHAT) {
                        if (drawerState.isOpen) coroutineScope.launch { drawerState.close() }
                        else currentScreen = Screen.CHAT
                    }

                    // Drawer
                    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color.Transparent,
                            drawerContentColor = TerminalWhite,
                            modifier = Modifier.width(280.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .glassPanel(shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp), alpha = 0.05f)
                                    .padding(horizontal = 16.dp, vertical = 24.dp)
                            ) {
                                Text("AntiGravity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TerminalWhite)
                                Spacer(Modifier.height(24.dp))

                                val menu: @Composable (String, ImageVector, Screen) -> Unit = { title, icon, screen ->
                                    val selected = currentScreen == screen
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (selected) TerminalGreen.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp))
                                            .clickable { currentScreen = screen; coroutineScope.launch { drawerState.close() } }
                                            .padding(vertical = 12.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, null, tint = if (selected) TerminalGreen else TerminalWhite, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (selected) TerminalGreen else TerminalWhite)
                                    }
                                }

                                menu("Chat", Icons.Default.Forum, Screen.CHAT)
                                menu("Editor", Icons.Default.Code, Screen.EDITOR)
                                menu("Settings", Icons.Default.Settings, Screen.SETTINGS)

                                Spacer(Modifier.height(24.dp))
                                HorizontalDivider(color = TerminalWhite.copy(alpha = 0.1f))

                                // Recents
                                Spacer(Modifier.height(16.dp))
                                Text("Recent Chats", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerminalWhite.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                val chatSessions by agentExecutor.sessions.collectAsState()
                                if (chatSessions.isEmpty()) {
                                    Text("No recent chats", fontSize = 13.sp, color = TerminalGray)
                                } else {
                                    chatSessions.take(10).forEach { session ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { agentExecutor.loadSession(session); currentScreen = Screen.CHAT; coroutineScope.launch { drawerState.close() } }
                                                .padding(vertical = 8.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = TerminalGray, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(session.title, fontSize = 13.sp, color = TerminalWhite.copy(alpha = 0.8f), maxLines = 1)
                                        }
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                // New Chat
                                Button(
                                    onClick = { agentExecutor.clearHistory(); currentScreen = Screen.CHAT; coroutineScope.launch { drawerState.close() } },
                                    colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("New Chat", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }) {
                        // Content
                        when (currentScreen) {
                            Screen.CHAT -> TerminalView(
                                messages = messages,
                                isProcessing = isProcessing,
                                onSendPrompt = sendPrompt,
                                onClearConsole = { agentExecutor.clearHistory() },
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                            )
                            Screen.EDITOR -> EditorView(
                                messages = messages,
                                isProcessing = isProcessing,
                                onSendPrompt = sendPrompt,
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                config = config, apiKey = apiKey, baseUrl = baseUrl, modelName = modelName
                            )
                            Screen.SETTINGS -> SettingsView(
                                apiKey = apiKey, onApiKeyChange = saveApiKey,
                                baseUrl = baseUrl, onBaseUrlChange = saveBaseUrl,
                                modelName = modelName, onModelNameChange = saveModelName,
                                config = config, onConfigChange = saveConfig
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            try {
                val prefs = getSharedPreferences("vibecoder_crash_log", Context.MODE_PRIVATE)
                prefs.edit().putString("last_crash", "CRASH: ${e.javaClass.name}: ${e.message}\n${e.stackTrace.take(15).joinToString("\n") { "  at $it" }}").commit()
            } catch (_: Throwable) {}
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TermuxRunner.unregisterReceiver(applicationContext)
    }

    private fun showCrashDialog(log: String) {
        val scrollView = ScrollView(this)
        val tv = TextView(this).apply {
            text = log; textSize = 11f; setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            val pad = (12 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        }
        scrollView.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("Previous Session Crashed")
            .setView(scrollView)
            .setPositiveButton("Copy Log") { d, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CrashLog", log))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show(); d.dismiss()
            }
            .setNegativeButton("Dismiss") { d, _ -> d.dismiss() }
            .show()
    }
}
