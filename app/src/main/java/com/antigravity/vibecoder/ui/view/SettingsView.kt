package com.antigravity.vibecoder.ui.view

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.data.Provider
import com.antigravity.vibecoder.data.TermuxRunner
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Supported providers matching OpenClaude's /provider command
private enum class SetupProvider(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val envVars: List<String>, // env vars needed
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyPlaceholder: String
) {
    OPENAI(
        "OpenAI", "GPT-4o, GPT-4.1, o3", Icons.Default.SmartToy, TerminalGreen,
        listOf("CLAUDE_CODE_USE_OPENAI=1", "OPENAI_API_KEY", "OPENAI_MODEL"),
        "https://api.openai.com/v1", "gpt-4o", "sk-..."
    ),
    ANTHROPIC(
        "Anthropic", "Claude Sonnet, Opus, Haiku", Icons.Default.Psychology, TerminalAmber,
        listOf("ANTHROPIC_API_KEY", "ANTHROPIC_MODEL"),
        "https://api.anthropic.com", "claude-sonnet-4-20250514", "sk-ant-..."
    ),
    GEMINI(
        "Google Gemini", "Gemini 2.5, 2.0 Flash", Icons.Default.AutoAwesome, TerminalCyan,
        listOf("GEMINI_API_KEY"),
        "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", "AI..."
    ),
    DEEPSEEK(
        "DeepSeek", "DeepSeek V3, R1", Icons.Default.Code, Color(0xFF4A9EFF),
        listOf("CLAUDE_CODE_USE_OPENAI=1", "OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL"),
        "https://api.deepseek.com/v1", "deepseek-chat", "sk-..."
    ),
    GITHUB_MODELS(
        "GitHub Models", "GPT-4o, Llama, Phi via GitHub", Icons.Default.AccountCircle, TerminalWhite,
        listOf("CLAUDE_CODE_USE_GITHUB=1", "GITHUB_TOKEN"),
        "https://models.inference.ai.azure.com", "gpt-4o", "ghp_..."
    ),
    OLLAMA(
        "Ollama (Local)", "Llama, Qwen, DeepSeek locally", Icons.Default.PhoneAndroid, TerminalGreen,
        listOf("CLAUDE_CODE_USE_OPENAI=1", "OPENAI_BASE_URL", "OPENAI_MODEL"),
        "http://localhost:11434/v1", "qwen2.5-coder:7b", ""
    ),
    OPENCODE_ZEN(
        "OpenCode Zen", "Pay-as-you-go AI gateway", Icons.Default.Bolt, Color(0xFF9C27B0),
        listOf("OPENCODE_API_KEY", "OPENAI_BASE_URL=https://opencode.ai/zen/v1", "OPENAI_MODEL=anthropic/claude-sonnet-4-20250514"),
        "https://opencode.ai/zen/v1", "anthropic/claude-sonnet-4-20250514", ""
    )
}

@Composable
fun SettingsView(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit,
    config: ConnectionConfig,
    onConfigChange: (ConnectionConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(0) } // 0=setup, 1=configure, 2=done
    var selectedProvider by remember { mutableStateOf<SetupProvider?>(null) }
    var inputApiKey by remember { mutableStateOf(apiKey) }
    var isInstalling by remember { mutableStateOf(false) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var isServerRunning by remember { mutableStateOf(false) }
    var openClaudeInstalled by remember { mutableStateOf(false) }

    // Check if OpenClaude is installed
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            TermuxRunner.executeCommand(context, "which openclaude 2>/dev/null || test -f ~/openclaude/dist/cli.mjs && echo 'found'", config.workspacePath)
        }
        openClaudeInstalled = result.stdout.trim().isNotEmpty()
        if (openClaudeInstalled) currentStep = 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Step indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepDot(1, currentStep >= 0, "Install")
            StepLine()
            StepDot(2, currentStep >= 1, "Provider")
            StepLine()
            StepDot(3, currentStep >= 2, "Done")
        }

        when (currentStep) {
            0 -> InstallStep(
                isInstalling = isInstalling,
                installLog = installLog,
                onInstall = {
                    isInstalling = true
                    installLog = emptyList()
                    scope.launch {
                        runInstaller(context, config.workspacePath) { step, log ->
                            installLog = installLog + log
                        }
                        isInstalling = false
                        openClaudeInstalled = true
                        currentStep = 1
                    }
                },
                onSkip = { currentStep = 1 }
            )

            1 -> ProviderStep(
                selectedProvider = selectedProvider,
                onSelectProvider = { selectedProvider = it },
                apiKey = inputApiKey,
                onApiKeyChange = { inputApiKey = it },
                onContinue = {
                    selectedProvider?.let { provider ->
                        // Save provider config
                        onBaseUrlChange(provider.defaultBaseUrl)
                        onModelNameChange(provider.defaultModel)
                        onApiKeyChange(inputApiKey)
                        scope.launch { saveOpenClaudeProfile(context, config.workspacePath, provider, inputApiKey) }
                        currentStep = 2
                    }
                },
                onBack = { currentStep = 0 }
            )

            2 -> DoneStep(
                provider = selectedProvider,
                isServerRunning = isServerRunning,
                onStartServer = {
                    scope.launch {
                        TermuxRunner.executeCommand(context, "openclaude serve --port ${config.port} &", config.workspacePath)
                        isServerRunning = true
                    }
                },
                onStopServer = {
                    scope.launch {
                        TermuxRunner.executeCommand(context, "pkill -f 'openclaude serve'", config.workspacePath)
                        isServerRunning = false
                    }
                },
                onReconfigure = { currentStep = 1 },
                config = config,
                onConfigChange = onConfigChange
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StepDot(number: Int, active: Boolean, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) TerminalGreen else Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = if (active) Color.Black else TerminalWhite.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = if (active) TerminalGreen else TerminalWhite.copy(alpha = 0.4f), fontSize = 10.sp)
    }
}

@Composable
private fun StepLine() {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
            .padding(horizontal = 8.dp)
            .background(TerminalWhite.copy(alpha = 0.15f))
    )
}

// === Step 1: Install ===
@Composable
private fun InstallStep(
    isInstalling: Boolean,
    installLog: List<String>,
    onInstall: () -> Unit,
    onSkip: () -> Unit
) {
    SectionCard {
        SectionHeader(Icons.Default.Download, "Install OpenClaude")
        Text(
            "OpenClaude is an AI coding agent with 200+ model support, tools, and file editing.",
            color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp
        )

        if (!isInstalling) {
            Text("Installation includes:", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            listOf(
                "Node.js & npm (via Termux packages)",
                "OpenClaude CLI (@gitlawb/openclaude)",
                "Provider profile configuration"
            ).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = TerminalGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item, color = TerminalWhite.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("Skip", color = TerminalWhite.copy(alpha = 0.6f))
                }
                Button(
                    onClick = onInstall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install", fontWeight = FontWeight.Medium)
                }
            }
        } else {
            Text("Installing...", color = TerminalGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { (installLog.size.coerceAtMost(8) / 8f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = TerminalGreen, trackColor = Color.White.copy(alpha = 0.1f)
            )
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)
            ) {
                Column {
                    installLog.takeLast(8).forEach { line ->
                        Text(line, color = TerminalWhite.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// === Step 2: Provider ===
@Composable
private fun ProviderStep(
    selectedProvider: SetupProvider?,
    onSelectProvider: (SetupProvider) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    SectionCard {
        SectionHeader(Icons.Default.Cloud, "Choose AI Provider")
        Text(
            "Select your provider and enter your API key. This mirrors the /provider setup in the OpenClaude CLI.",
            color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp
        )

        SetupProvider.entries.forEach { provider ->
            val isSelected = selectedProvider == provider
            val bgColor = if (isSelected) TerminalGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
            val borderColor = if (isSelected) TerminalGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable { onSelectProvider(provider) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(provider.icon, null, tint = provider.color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.displayName, color = TerminalWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(provider.description, color = TerminalWhite.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = TerminalGreen, modifier = Modifier.size(18.dp))
                }
            }
        }

        // API Key input (shown when provider selected and needs a key)
        AnimatedVisibility(visible = selectedProvider != null && selectedProvider.keyPlaceholder.isNotEmpty()) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    placeholder = { Text(selectedProvider?.keyPlaceholder ?: "") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = inputFieldColors(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                // Show env vars that will be set
                Spacer(modifier = Modifier.height(4.dp))
                Text("Environment variables:", color = TerminalWhite.copy(alpha = 0.4f), fontSize = 10.sp)
                selectedProvider?.envVars?.forEach { env ->
                    Text("  $env", color = TerminalGreen.copy(alpha = 0.6f), fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }

        // Continue button
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back", color = TerminalWhite.copy(alpha = 0.6f))
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
                enabled = selectedProvider != null,
                colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen, disabledContainerColor = Color.Gray.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Medium)
            }
        }
    }
}

// === Step 3: Done ===
@Composable
private fun DoneStep(
    provider: SetupProvider?,
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onReconfigure: () -> Unit,
    config: ConnectionConfig,
    onConfigChange: (ConnectionConfig) -> Unit
) {
    SectionCard {
        SectionHeader(Icons.Default.CheckCircle, "Setup Complete")
        Text(
            "Provider: ${provider?.displayName ?: "None"}",
            color = TerminalGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
        Text(
            "Model: ${provider?.defaultModel ?: "None"}",
            color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Server control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("OpenClaude Server", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (isServerRunning) "Running on port ${config.port}" else "Stopped",
                    color = if (isServerRunning) TerminalGreen else TerminalWhite.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isServerRunning) {
                    Button(
                        onClick = onStartServer,
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start")
                    }
                } else {
                    OutlinedButton(onClick = onStopServer, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp), tint = TerminalRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", color = TerminalRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Server config
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput("Port", config.port, { onConfigChange(config.copy(port = it)) }, Modifier.weight(1f))
            NumberInput("gRPC Port", config.grpcPort, { onConfigChange(config.copy(grpcPort = it)) }, Modifier.weight(1f))
        }
        TextInput("Working Dir", config.workspacePath, { onConfigChange(config.copy(workspacePath = it)) })

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onReconfigure,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reconfigure", color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onReconfigure,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Switch Provider", color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}

// === Helper Components ===

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TerminalGreen, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = TerminalWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        colors = inputFieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

@Composable
private fun NumberInput(label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value.toString(), onValueChange = { onValueChange(it.toIntOrNull() ?: value) },
        label = { Text(label) }, modifier = modifier, singleLine = true,
        colors = inputFieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

@Composable
private fun inputFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TerminalGreen.copy(alpha = 0.5f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
    cursorColor = TerminalGreen,
    focusedTextColor = TerminalWhite,
    unfocusedTextColor = TerminalWhite,
    focusedLabelColor = TerminalGreen.copy(alpha = 0.7f),
    unfocusedLabelColor = TerminalWhite.copy(alpha = 0.4f)
)

// === Installer & Profile ===

private suspend fun runInstaller(
    context: android.content.Context,
    workspacePath: String,
    onLog: (String) -> Unit
) = withContext(Dispatchers.IO) {
    val steps = listOf(
        "pkg update -y && pkg install -y nodejs-lts git" to "Installing Node.js & git",
        "npm install -g @gitlawb/openclaude@latest 2>&1" to "Installing OpenClaude CLI"
    )
    for ((cmd, desc) in steps) {
        onLog(">>> $desc")
        val result = TermuxRunner.executeCommand(context, cmd, workspacePath)
        val output = result.stdout.trim().lines().lastOrNull() ?: ""
        if (result.exitCode == 0) onLog("  OK: $output") else onLog("  WARN: ${result.stderr?.trim()?.take(80) ?: output}")
    }
    onLog("Installation complete!")
}

private suspend fun saveOpenClaudeProfile(
    context: android.content.Context,
    workspacePath: String,
    provider: SetupProvider,
    apiKey: String
) = withContext(Dispatchers.IO) {
    // Set environment variables for the selected provider
    val envExports = buildString {
        provider.envVars.forEach { envVar ->
            val (name, value) = if (envVar.contains("=")) {
                envVar.split("=", limit = 2)
            } else {
                envVar to apiKey
            }
            appendLine("export $name=\"$value\"")
        }
    }

    // Save to .bashrc for persistence
    val bashrcCmd = """
        cat >> ~/.bashrc << 'ENVEOF'
$envExports
ENVEOF
    """.trimIndent()
    TermuxRunner.executeCommand(context, bashrcCmd, workspacePath)

    // Create the OpenClaude profile JSON
    val profileJson = """
        {
            "provider": "${provider.name.lowercase()}",
            "model": "${provider.defaultModel}",
            "base_url": "${provider.defaultBaseUrl}",
            "api_key": "$apiKey"
        }
    """.trimIndent()

    val profileCmd = """
        mkdir -p ~/.openclaude && cat > ~/.openclaude/.openclaude-profile.json << 'PROFILE'
$profileJson
PROFILE
    """.trimIndent()
    TermuxRunner.executeCommand(context, profileCmd, workspacePath)
}
