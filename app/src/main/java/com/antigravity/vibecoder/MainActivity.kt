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
import com.antigravity.vibecoder.ui.theme.*
import com.antigravity.vibecoder.ui.view.EditorView
import com.antigravity.vibecoder.ui.view.SettingsView
import com.antigravity.vibecoder.ui.view.TerminalView
import kotlinx.coroutines.launch

enum class Screen { CHAT, EDITOR, SETTINGS }

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var agentExecutor: AgentExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            prefs = getSharedPreferences("vibecoder_prefs", Context.MODE_PRIVATE)
            agentExecutor = AgentExecutor(applicationContext)

            val crashLog = VibeCoderApplication.consumeLastCrashLog(this)
            if (crashLog != null) showCrashDialog(crashLog)

            setContent {
                AntiGravityVibeCoderTheme {
                    fun SharedPreferences.str(key: String, def: String) = try { getString(key, def) ?: def } catch (_: Exception) { def }
                    fun SharedPreferences.int(key: String, def: Int) = try { getInt(key, def) } catch (_: Exception) { def }

                    LaunchedEffect(Unit) { if (crashLog != null) agentExecutor.injectCrashLog(crashLog) }

                    var apiKey by remember { mutableStateOf(prefs.str("api_key", "")) }
                    var baseUrl by remember { mutableStateOf(prefs.str("base_url", com.antigravity.vibecoder.data.Provider.OPENAI.defaultBaseUrl)) }
                    var modelName by remember { mutableStateOf(prefs.str("model_name", com.antigravity.vibecoder.data.Provider.OPENAI.defaultModel)) }
                    var execModeStr by remember { mutableStateOf(prefs.str("exec_mode", ExecutionMode.OPENCLAUDE.name)) }
                    val execMode = try { ExecutionMode.valueOf(execModeStr) } catch (_: Exception) { ExecutionMode.OPENCLAUDE }
                    var sshHost by remember { mutableStateOf(prefs.str("ssh_host", "127.0.0.1")) }
                    var sshPort by remember { mutableIntStateOf(prefs.int("ssh_port", 8022)) }
                    var sshUser by remember { mutableStateOf(prefs.str("ssh_user", "android")) }
                    var sshPass by remember { mutableStateOf(prefs.str("ssh_pass", "")) }
                    var sshWork by remember { mutableStateOf(prefs.str("ssh_work", "/data/data/com.termux/files/home")) }
                    val grpcPort by remember { mutableIntStateOf(prefs.int("grpc_port", 50051)) }

                    val config = remember(execMode, sshHost, sshPort, sshUser, sshPass, sshWork, grpcPort) {
                        ConnectionConfig(execMode, sshHost, sshPort, grpcPort, sshUser, sshPass, sshWork)
                    }

                    var screen by remember { mutableStateOf(Screen.CHAT) }

                    LaunchedEffect(screen) {
                        requestedOrientation = if (screen == Screen.EDITOR) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }

                    val messages by agentExecutor.messages.collectAsState()
                    val isProcessing by agentExecutor.isProcessing.collectAsState()
                    val scope = rememberCoroutineScope()

                    val save: (String, String, String) -> Unit = { k, v, _ -> prefs.edit().putString(k, v).apply() }
                    val saveConfig: (ConnectionConfig) -> Unit = { c ->
                        execModeStr = c.executionMode.name; sshHost = c.host; sshPort = c.port; sshUser = c.user; sshPass = c.passwordKey; sshWork = c.workspacePath
                        prefs.edit().putString("exec_mode", c.executionMode.name).putString("ssh_host", c.host).putInt("ssh_port", c.port)
                            .putString("ssh_user", c.user).putString("ssh_pass", c.passwordKey).putString("ssh_work", c.workspacePath).apply()
                    }

                    val sendPrompt: (String) -> Unit = { prompt ->
                        scope.launch { agentExecutor.executeUserPrompt(prompt, apiKey, baseUrl, modelName, config) }
                    }

                    val drawerState = rememberDrawerState(DrawerValue.Closed)

                    BackHandler(enabled = drawerState.isOpen || screen != Screen.CHAT) {
                        if (drawerState.isOpen) scope.launch { drawerState.close() } else screen = Screen.CHAT
                    }

                    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
                        ModalDrawerSheet(drawerContainerColor = Color.Transparent, modifier = Modifier.width(280.dp)) {
                            Column(
                                modifier = Modifier.fillMaxSize().glassPanel(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp), 0.05f)
                                    .padding(horizontal = 16.dp, vertical = 24.dp)
                            ) {
                                Text("AntiGravity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TerminalWhite)
                                Spacer(Modifier.height(24.dp))

                                fun menu(label: String, icon: ImageVector, s: Screen) {
                                    val sel = screen == s
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(if (sel) TerminalGreen.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp))
                                            .clickable { screen = s; scope.launch { drawerState.close() } }.padding(vertical = 12.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, null, tint = if (sel) TerminalGreen else TerminalWhite, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (sel) TerminalGreen else TerminalWhite)
                                    }
                                }

                                menu("Chat", Icons.Default.Forum, Screen.CHAT)
                                menu("Editor", Icons.Default.Code, Screen.EDITOR)
                                menu("Settings", Icons.Default.Settings, Screen.SETTINGS)

                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = TerminalWhite.copy(alpha = 0.1f))
                                Spacer(Modifier.height(16.dp))

                                Text("Recent Chats", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TerminalWhite.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                                val sessions by agentExecutor.sessions.collectAsState()
                                if (sessions.isEmpty()) {
                                    Text("No recent chats", fontSize = 13.sp, color = TerminalGray)
                                } else {
                                    sessions.take(10).forEach { session ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                agentExecutor.loadSession(session); screen = Screen.CHAT; scope.launch { drawerState.close() }
                                            }.padding(vertical = 8.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Chat, null, tint = TerminalGray, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(session.title, fontSize = 13.sp, color = TerminalWhite.copy(alpha = 0.8f), maxLines = 1)
                                        }
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                Button(
                                    onClick = { agentExecutor.clearHistory(); screen = Screen.CHAT; scope.launch { drawerState.close() } },
                                    colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen),
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("New Chat", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }) {
                        when (screen) {
                            Screen.CHAT -> TerminalView(messages, isProcessing, sendPrompt, { agentExecutor.clearHistory() }, { scope.launch { drawerState.open() } })
                            Screen.EDITOR -> EditorView(onOpenDrawer = { scope.launch { drawerState.open() } }, config = config)
                            Screen.SETTINGS -> SettingsView(
                                apiKey, { apiKey = it; save("api_key", it, "") },
                                baseUrl, { baseUrl = it; save("base_url", it, "") },
                                modelName, { modelName = it; save("model_name", it, "") },
                                config, saveConfig
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            try {
                getSharedPreferences("vibecoder_crash_log", Context.MODE_PRIVATE).edit()
                    .putString("last_crash", "CRASH: ${e.javaClass.name}: ${e.message}\n${e.stackTrace.take(15).joinToString("\n") { "  at $it" }}").commit()
            } catch (_: Throwable) {}
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TermuxRunner.unregisterReceiver(applicationContext)
    }

    private fun showCrashDialog(log: String) {
        val tv = TextView(this).apply {
            text = log; textSize = 11f; setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            val p = (12 * resources.displayMetrics.density).toInt(); setPadding(p, p, p, p)
        }
        AlertDialog.Builder(this).setTitle("Previous Session Crashed")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Copy") { d, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CrashLog", log))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show(); d.dismiss()
            }
            .setNegativeButton("Dismiss") { d, _ -> d.dismiss() }.show()
    }
}
