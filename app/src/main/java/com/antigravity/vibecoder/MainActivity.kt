package com.antigravity.vibecoder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.data.AgentExecutor
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import com.antigravity.vibecoder.ui.theme.AntiGravityVibeCoderTheme
import com.antigravity.vibecoder.ui.theme.DarkBackground
import com.antigravity.vibecoder.ui.theme.DarkBorder
import com.antigravity.vibecoder.ui.theme.DarkSurface
import com.antigravity.vibecoder.ui.theme.TerminalCyan
import com.antigravity.vibecoder.ui.theme.TerminalGreen
import com.antigravity.vibecoder.ui.theme.TerminalGreenDim
import com.antigravity.vibecoder.ui.theme.TerminalGray
import com.antigravity.vibecoder.ui.view.EditorView
import com.antigravity.vibecoder.ui.view.PreviewView
import com.antigravity.vibecoder.ui.view.SettingsView
import com.antigravity.vibecoder.ui.view.TerminalView
import kotlinx.coroutines.launch

enum class Screen {
    TERMINAL,
    EDITOR,
    PREVIEW,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var agentExecutor: AgentExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("vibecoder_prefs", Context.MODE_PRIVATE)
        agentExecutor = AgentExecutor(applicationContext)

        setContent {
            AntiGravityVibeCoderTheme {
                // Settings States
                var apiKey by remember { mutableStateOf(sharedPreferences.getString("api_key", "") ?: "") }
                var baseUrl by remember { mutableStateOf(sharedPreferences.getString("base_url", "https://opencode.ai/zen/v1") ?: "https://opencode.ai/zen/v1") }
                var modelName by remember { mutableStateOf(sharedPreferences.getString("model_name", "opencode/zen-coder-1") ?: "opencode/zen-coder-1") }
                
                var executionModeStr by remember { mutableStateOf(sharedPreferences.getString("execution_mode", ExecutionMode.SANDBOX.name) ?: ExecutionMode.SANDBOX.name) }
                val executionMode = try { ExecutionMode.valueOf(executionModeStr) } catch(e: Exception) { ExecutionMode.SANDBOX }
                
                var sshHost by remember { mutableStateOf(sharedPreferences.getString("ssh_host", "127.0.0.1") ?: "127.0.0.1") }
                var sshPort by remember { mutableStateOf(sharedPreferences.getInt("ssh_port", 8022)) }
                var sshUser by remember { mutableStateOf(sharedPreferences.getString("ssh_user", "android") ?: "android") }
                var sshPass by remember { mutableStateOf(sharedPreferences.getString("ssh_pass", "") ?: "") }
                var sshWorkspace by remember {
                    mutableStateOf(
                        sharedPreferences.getString("ssh_workspace", "/data/data/com.termux/files/home") 
                            ?: "/data/data/com.termux/files/home"
                    )
                }

                val config = ConnectionConfig(
                    executionMode = executionMode,
                    host = sshHost,
                    port = sshPort,
                    user = sshUser,
                    passwordKey = sshPass,
                    workspacePath = sshWorkspace
                )

                // Navigation State
                var currentScreen by remember { mutableStateOf(Screen.TERMINAL) }
                
                // Agent History states
                val messages by agentExecutor.messages.collectAsState()
                val isProcessing by agentExecutor.isProcessing.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                
                var previewUrl by remember { mutableStateOf("") }

                // Auto-detect localhost URLs in new messages
                LaunchedEffect(messages.size) {
                    val lastMsg = messages.lastOrNull()
                    if (lastMsg != null && lastMsg.type == MessageType.TOOL_OUTPUT) {
                        val urlRegex = "http://localhost:\\d+".toRegex()
                        val match = urlRegex.find(lastMsg.text)
                        if (match != null) {
                            val url = match.value
                            if (url != previewUrl) {
                                previewUrl = url
                                currentScreen = Screen.PREVIEW
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    applicationContext.startActivity(intent)
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }

                // Save configurations helpers
                val saveApiKey: (String) -> Unit = {
                    apiKey = it
                    sharedPreferences.edit().putString("api_key", it).apply()
                }
                val saveBaseUrl: (String) -> Unit = {
                    baseUrl = it
                    sharedPreferences.edit().putString("base_url", it).apply()
                }
                val saveModelName: (String) -> Unit = {
                    modelName = it
                    sharedPreferences.edit().putString("model_name", it).apply()
                }
                val saveConfig: (ConnectionConfig) -> Unit = { newConfig ->
                    executionModeStr = newConfig.executionMode.name
                    sshHost = newConfig.host
                    sshPort = newConfig.port
                    sshUser = newConfig.user
                    sshPass = newConfig.passwordKey
                    sshWorkspace = newConfig.workspacePath
                    
                    sharedPreferences.edit().apply {
                        putString("execution_mode", newConfig.executionMode.name)
                        putString("ssh_host", newConfig.host)
                        putInt("ssh_port", newConfig.port)
                        putString("ssh_user", newConfig.user)
                        putString("ssh_pass", newConfig.passwordKey)
                        putString("ssh_workspace", newConfig.workspacePath)
                    }.apply()
                }

                Scaffold(
                    bottomBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface)
                                .border(1.dp, color = DarkBorder)
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomTabItem(
                                title = "TERMINAL",
                                icon = Icons.Default.Terminal,
                                isSelected = currentScreen == Screen.TERMINAL,
                                onClick = { currentScreen = Screen.TERMINAL }
                            )
                            BottomTabItem(
                                title = "EDITOR",
                                icon = Icons.Default.Code,
                                isSelected = currentScreen == Screen.EDITOR,
                                onClick = { currentScreen = Screen.EDITOR }
                            )
                            BottomTabItem(
                                title = "PREVIEW",
                                icon = Icons.Default.Visibility,
                                isSelected = currentScreen == Screen.PREVIEW,
                                onClick = { currentScreen = Screen.PREVIEW }
                            )
                            BottomTabItem(
                                title = "SETTINGS",
                                icon = Icons.Default.Settings,
                                isSelected = currentScreen == Screen.SETTINGS,
                                onClick = { currentScreen = Screen.SETTINGS }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            Screen.TERMINAL -> {
                                TerminalView(
                                    messages = messages,
                                    isProcessing = isProcessing,
                                    onSendPrompt = { prompt ->
                                        if (apiKey.isEmpty()) {
                                            agentExecutor.clearHistory()
                                            coroutineScope.launch {
                                                agentExecutor.executeUserPrompt("Please go to SETTINGS and set your OpenCode Zen API key first.", "", "", "", config)
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                agentExecutor.executeUserPrompt(prompt, apiKey, baseUrl, modelName, config)
                                            }
                                        }
                                    },
                                    onClearConsole = {
                                        agentExecutor.clearHistory()
                                    }
                                )
                            }
                            Screen.EDITOR -> {
                                EditorView(
                                    config = config,
                                    onSendPrompt = { prompt ->
                                        if (apiKey.isEmpty()) {
                                            agentExecutor.clearHistory()
                                            coroutineScope.launch {
                                                agentExecutor.executeUserPrompt("Please go to SETTINGS and set your OpenCode Zen API key first.", "", "", "", config)
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                agentExecutor.executeUserPrompt(prompt, apiKey, baseUrl, modelName, config)
                                            }
                                        }
                                        currentScreen = Screen.TERMINAL
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Screen.PREVIEW -> {
                                PreviewView(
                                    url = previewUrl,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsView(
                                    apiKey = apiKey,
                                    onApiKeyChange = saveApiKey,
                                    baseUrl = baseUrl,
                                    onBaseUrlChange = saveBaseUrl,
                                    modelName = modelName,
                                    onModelNameChange = saveModelName,
                                    config = config,
                                    onConfigChange = saveConfig
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.BottomTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) TerminalGreen else TerminalGray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            color = if (isSelected) TerminalGreen else TerminalGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
