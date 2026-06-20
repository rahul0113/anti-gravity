package com.antigravity.vibecoder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
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
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.antigravity.vibecoder.data.AgentExecutor
import com.antigravity.vibecoder.data.TermuxRunner
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.MessageType
import com.antigravity.vibecoder.ui.theme.AntiGravityVibeCoderTheme
import com.antigravity.vibecoder.ui.theme.DarkBorder
import com.antigravity.vibecoder.ui.theme.DarkSurface
import com.antigravity.vibecoder.ui.theme.TerminalGreen
import com.antigravity.vibecoder.ui.theme.TerminalGray
import com.antigravity.vibecoder.ui.view.EditorView
import com.antigravity.vibecoder.ui.view.PreviewView
import com.antigravity.vibecoder.ui.view.SettingsView
import com.antigravity.vibecoder.ui.view.TerminalView
import kotlinx.coroutines.launch

enum class Screen { TERMINAL, EDITOR, PREVIEW, SETTINGS }

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var agentExecutor: AgentExecutor
    // S-4 FIX: Track whether we're using encrypted or plaintext prefs
    private var isUsingSecurePrefs = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CRASH-1 FIX: Catch Throwable (not just Exception) so NoClassDefFoundError on API 21-22
        // is handled gracefully and we fall back to plain SharedPreferences
        // CRASH-2 FIX: Consistent pref file name so fallback doesn't lose data
        sharedPreferences = try {
            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                applicationContext,
                "vibecoder_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { isUsingSecurePrefs = true }
        } catch (e: Throwable) {
            isUsingSecurePrefs = false
            getSharedPreferences("vibecoder_prefs", Context.MODE_PRIVATE)
        }

        agentExecutor = AgentExecutor(applicationContext)

        setContent {
            AntiGravityVibeCoderTheme {
                var apiKey by remember { mutableStateOf(sharedPreferences.getString("api_key", "") ?: "") }
                var baseUrl by remember { mutableStateOf(sharedPreferences.getString("base_url", "https://opencode.ai/zen/v1") ?: "https://opencode.ai/zen/v1") }
                var modelName by remember { mutableStateOf(sharedPreferences.getString("model_name", "opencode/zen-coder-1") ?: "opencode/zen-coder-1") }
                var executionModeStr by remember { mutableStateOf(sharedPreferences.getString("execution_mode", ExecutionMode.SANDBOX.name) ?: ExecutionMode.SANDBOX.name) }
                val executionMode = try { ExecutionMode.valueOf(executionModeStr) } catch (e: Exception) { ExecutionMode.SANDBOX }
                var sshHost by remember { mutableStateOf(sharedPreferences.getString("ssh_host", "127.0.0.1") ?: "127.0.0.1") }
                var sshPort by remember { mutableStateOf(sharedPreferences.getInt("ssh_port", 8022)) }
                var sshUser by remember { mutableStateOf(sharedPreferences.getString("ssh_user", "android") ?: "android") }
                var sshPass by remember { mutableStateOf(sharedPreferences.getString("ssh_pass", "") ?: "") }
                var sshWorkspace by remember {
                    mutableStateOf(sharedPreferences.getString("ssh_workspace", "/data/data/com.termux/files/home") ?: "/data/data/com.termux/files/home")
                }

                // ARCH-2 FIX: remember config so it isn't recreated on every recomposition,
                // which was causing EditorView's LaunchedEffect to fire in an infinite loop
                val config = remember(executionMode, sshHost, sshPort, sshUser, sshPass, sshWorkspace) {
                    ConnectionConfig(
                        executionMode = executionMode,
                        host = sshHost,
                        port = sshPort,
                        user = sshUser,
                        passwordKey = sshPass,
                        workspacePath = sshWorkspace
                    )
                }

                var currentScreen by remember { mutableStateOf(Screen.TERMINAL) }

                // BUG-5 FIX: rememberSaveable persists orientation state across configuration changes
                var isLandscape by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(currentScreen) {
                    if (currentScreen == Screen.TERMINAL || currentScreen == Screen.SETTINGS) {
                        isLandscape = false
                        this@MainActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }

                val messages by agentExecutor.messages.collectAsState()
                val isProcessing by agentExecutor.isProcessing.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                var previewUrl by remember { mutableStateOf("") }

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
                                } catch (e: Exception) { /* ignore */ }
                            }
                        }
                    }
                }

                val saveApiKey: (String) -> Unit = { apiKey = it; sharedPreferences.edit().putString("api_key", it).apply() }
                val saveBaseUrl: (String) -> Unit = { baseUrl = it; sharedPreferences.edit().putString("base_url", it).apply() }
                val saveModelName: (String) -> Unit = { modelName = it; sharedPreferences.edit().putString("model_name", it).apply() }
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
                        // S-4 FIX: Only persist SSH password if using encrypted prefs
                        // On insecure fallback prefs, skip saving the password to avoid plaintext storage
                        if (isUsingSecurePrefs) putString("ssh_pass", newConfig.passwordKey)
                        putString("ssh_workspace", newConfig.workspacePath)
                    }.apply()
                }

                val sendPrompt: (String) -> Unit = { prompt ->
                    if (apiKey.isEmpty() && config.executionMode != ExecutionMode.SANDBOX) {
                        coroutineScope.launch {
                            agentExecutor.executeUserPrompt("Please go to SETTINGS and set your API key first.", "", "", "", config)
                        }
                    } else {
                        coroutineScope.launch {
                            agentExecutor.executeUserPrompt(prompt, apiKey, baseUrl, modelName, config)
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface)
                                .border(1.dp, DarkBorder)
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomTabItem("TERMINAL", Icons.Default.Terminal, currentScreen == Screen.TERMINAL) { currentScreen = Screen.TERMINAL }
                            BottomTabItem("EDITOR", Icons.Default.Code, currentScreen == Screen.EDITOR) { currentScreen = Screen.EDITOR }
                            BottomTabItem("PREVIEW", Icons.Default.Visibility, currentScreen == Screen.PREVIEW) { currentScreen = Screen.PREVIEW }
                            BottomTabItem("SETTINGS", Icons.Default.Settings, currentScreen == Screen.SETTINGS) { currentScreen = Screen.SETTINGS }

                            // Rotate button only on Editor & Preview
                            if (currentScreen == Screen.EDITOR || currentScreen == Screen.PREVIEW) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            isLandscape = !isLandscape
                                            this@MainActivity.requestedOrientation = if (isLandscape)
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            else
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Rotate Screen",
                                        tint = if (isLandscape) TerminalGreen else TerminalGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isLandscape) "LAND" else "PORT",
                                        color = if (isLandscape) TerminalGreen else TerminalGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (currentScreen) {
                            Screen.TERMINAL -> TerminalView(
                                messages = messages,
                                isProcessing = isProcessing,
                                onSendPrompt = { sendPrompt(it); },
                                onClearConsole = { agentExecutor.clearHistory() }
                            )
                            Screen.EDITOR -> EditorView(
                                config = config,
                                onSendPrompt = { sendPrompt(it); currentScreen = Screen.TERMINAL },
                                modifier = Modifier.fillMaxSize()
                            )
                            Screen.PREVIEW -> PreviewView(url = previewUrl, modifier = Modifier.fillMaxSize())
                            Screen.SETTINGS -> SettingsView(
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

    // CRASH-4 FIX: Properly unregister BroadcastReceiver when Activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        TermuxRunner.unregisterReceiver(applicationContext)
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            color = if (isSelected) TerminalGreen else TerminalGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
