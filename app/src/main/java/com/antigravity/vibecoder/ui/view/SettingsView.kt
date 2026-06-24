package com.antigravity.vibecoder.ui.view

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isSshPasswordVisible by remember { mutableStateOf(false) }

    // Installer state
    var isInstalling by remember { mutableStateOf(false) }
    var installStep by remember { mutableStateOf("") }
    var installProgress by remember { mutableFloatStateOf(0f) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var openClaudeInstalled by remember { mutableStateOf(false) }
    var showInstaller by remember { mutableStateOf(false) }

    // Check if openclaude is already installed
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            TermuxRunner.executeCommand(context, "which openclaude 2>/dev/null || test -f ~/openclaude/dist/cli.mjs && echo 'found'", config.workspacePath)
        }
        openClaudeInstalled = result.stdout.trim().isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // === OpenClaude Setup ===
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TerminalGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SmartToy, null, tint = TerminalGreen, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("OpenClaude AI Agent", color = TerminalWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (openClaudeInstalled) "Installed and ready" else "One-click install from GitHub",
                        color = if (openClaudeInstalled) TerminalGreen else TerminalWhite.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                if (openClaudeInstalled) {
                    Icon(Icons.Filled.CheckCircle, null, tint = TerminalGreen, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!showInstaller) {
                // Main button
                Button(
                    onClick = {
                        if (openClaudeInstalled) {
                            // Toggle server
                            onConfigChange(config.copy(
                                executionMode = if (config.executionMode == ExecutionMode.OPENCLAUDE) ExecutionMode.SANDBOX else ExecutionMode.OPENCLAUDE
                            ))
                        } else {
                            showInstaller = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (openClaudeInstalled) {
                            if (config.executionMode == ExecutionMode.OPENCLAUDE) TerminalGreen else Color.Gray.copy(alpha = 0.3f)
                        } else TerminalGreen
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (openClaudeInstalled) Icons.Filled.PowerSettingsNew else Icons.Filled.Download,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            config.executionMode == ExecutionMode.OPENCLAUDE -> "AI Agent Active"
                            openClaudeInstalled -> "Enable AI Agent"
                            else -> "Install OpenClaude"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Installer UI
            AnimatedVisibility(visible = showInstaller) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isInstalling) {
                        // Pre-install: show what will happen
                        Text(
                            "This will install OpenClaude from GitHub into Termux. You need ~700MB free space.",
                            color = TerminalWhite.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )

                        Text("What gets installed:", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        listOf(
                            "Node.js, git, proot-distro",
                            "Ubuntu environment (proot)",
                            "Bun runtime (inside Ubuntu)",
                            "OpenClaude CLI from GitHub"
                        ).forEach { step ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Check, null, tint = TerminalGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(step, color = TerminalWhite.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }

                        // Provider key input (optional before install)
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = onApiKeyChange,
                            label = { Text("API Key (optional, set later)") },
                            placeholder = { Text("sk-... or OpenRouter key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        if (isApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        null,
                                        tint = TerminalWhite.copy(alpha = 0.5f)
                                    )
                                }
                            },
                            colors = inputFieldColors(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showInstaller = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", color = TerminalWhite.copy(alpha = 0.6f))
                            }
                            Button(
                                onClick = {
                                    isInstalling = true
                                    installProgress = 0f
                                    installLog = emptyList()
                                    scope.launch {
                                        runInstaller(context, config.workspacePath) { step, progress, log ->
                                            installStep = step
                                            installProgress = progress
                                            if (log != null) installLog = installLog + log
                                        }
                                        isInstalling = false
                                        openClaudeInstalled = true
                                        showInstaller = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install", fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        // Installing: show progress
                        Text("Installing OpenClaude...", color = TerminalWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(installStep, color = TerminalGreen, fontSize = 12.sp)

                        LinearProgressIndicator(
                            progress = { installProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TerminalGreen,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        // Log output
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                installLog.forEach { line ->
                                    Text(
                                        line,
                                        color = TerminalWhite.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                }
                                }
                            }
                        }
                    }
                }
            }
        }

        // === Server Config (when OpenClaude enabled) ===
        if (config.executionMode == ExecutionMode.OPENCLAUDE && openClaudeInstalled) {
            SectionCard {
                SectionHeader(Icons.Filled.Tune, "Server Config")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput("HTTP Port", config.port, { onConfigChange(config.copy(port = it)) }, Modifier.weight(1f))
                    NumberInput("gRPC Port", config.grpcPort, { onConfigChange(config.copy(grpcPort = it)) }, Modifier.weight(1f))
                }
                TextInput("Working Dir", config.workspacePath, { onConfigChange(config.copy(workspacePath = it)) })
            }
        }

        // === AI Provider (for sandbox mode or API key) ===
        SectionCard {
            SectionHeader(Icons.Filled.Cloud, "AI Provider")
            Text(
                "For direct API chat (without OpenClaude agent)",
                color = TerminalWhite.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Provider.entries.filter { it != Provider.OPENCLAUDE }.forEach { provider ->
                    val selected = baseUrl == provider.defaultBaseUrl
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onBaseUrlChange(provider.defaultBaseUrl)
                            onModelNameChange(provider.defaultModel)
                        },
                        label = { Text(provider.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TerminalGreen.copy(alpha = 0.2f),
                            selectedLabelColor = TerminalGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            if (apiKey.isNotBlank() || config.executionMode != ExecutionMode.OPENCLAUDE) {
                Spacer(modifier = Modifier.height(8.dp))
                TextInput("API Key", apiKey, onApiKeyChange, isPassword = true, isPasswordVisible = isApiKeyVisible, onTogglePassword = { isApiKeyVisible = !isApiKeyVisible })
            }

            if (config.executionMode != ExecutionMode.OPENCLAUDE) {
                TextInput("Base URL", baseUrl, onBaseUrlChange)
                TextInput("Model", modelName, onModelNameChange)
            }
        }

        // === SSH (optional remote execution) ===
        SectionCard {
            SectionHeader(Icons.Filled.Security, "SSH Remote")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Remote machine execution", color = TerminalWhite, fontSize = 13.sp)
                    Text("Connect to a remote dev box or VPS", color = TerminalWhite.copy(alpha = 0.4f), fontSize = 11.sp)
                }
                Switch(
                    checked = config.executionMode == ExecutionMode.SSH,
                    onCheckedChange = { onConfigChange(config.copy(executionMode = if (it) ExecutionMode.SSH else ExecutionMode.SANDBOX)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = TerminalGreen)
                )
            }

            AnimatedVisibility(visible = config.executionMode == ExecutionMode.SSH) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("Host", config.host, { onConfigChange(config.copy(host = it)) }, placeholder = "192.168.1.100")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInput("Port", config.port, { onConfigChange(config.copy(port = it)) }, Modifier.weight(1f))
                        TextInput("User", config.user, { onConfigChange(config.copy(user = it)) }, Modifier.weight(1f), placeholder = "android")
                    }
                    TextInput("Password / Key", config.passwordKey, { onConfigChange(config.copy(passwordKey = it)) }, isPassword = true, isPasswordVisible = isSshPasswordVisible, onTogglePassword = { isSshPasswordVisible = !isSshPasswordVisible })
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// === Helper Components ===

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
private fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder, color = TerminalWhite.copy(alpha = 0.3f)) }} else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            if (isPassword && onTogglePassword != null) {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        null,
                        tint = TerminalWhite.copy(alpha = 0.5f)
                    )
                }
            }
        },
        colors = inputFieldColors(),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

@Composable
private fun NumberInput(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onValueChange(it.toIntOrNull() ?: value) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        colors = inputFieldColors(),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
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

// === Installer ===

private suspend fun runInstaller(
    context: android.content.Context,
    workspacePath: String,
    onProgress: (step: String, progress: Float, log: String?) -> Unit
) = withContext(Dispatchers.IO) {
    data class Step(val name: String, val command: String, val progress: Float)

    val steps = listOf(
        Step("Installing Node.js & dependencies", "pkg update -y && pkg install -y nodejs-lts git proot-distro", 0.15f),
        Step("Cloning OpenClaude from GitHub", "cd ~ && rm -rf openclaude && git clone https://github.com/Gitlawb/openclaude.git", 0.30f),
        Step("Installing npm packages", "cd ~/openclaude && npm install 2>&1", 0.45f),
        Step("Setting up proot Ubuntu", "proot-distro install ubuntu 2>&1", 0.60f),
        Step("Installing Bun in Ubuntu", "proot-distro login ubuntu -- bash -c 'curl -fsSL https://bun.sh/install | bash && source ~/.bashrc' 2>&1", 0.75f),
        Step("Building OpenClaude", "proot-distro login ubuntu -- bash -c 'cd /data/data/com.termux/files/home/openclaude && bun run build' 2>&1", 0.90f),
        Step("Creating launcher script", "cat > ~/openclaude-launch.sh << 'LAUNCHER'\n#!/data/data/com.termux/files/usr/bin/bash\nproot-distro login ubuntu -- bash -c 'cd /data/data/com.termux/files/home/openclaude && source ~/.bashrc && node dist/cli.mjs \"\\$@\"'\nLAUNCHER\nchmod +x ~/openclaude-launch.sh", 0.95f),
        Step("Linking openclaude command", "ln -sf ~/openclaude-launch.sh /data/data/com.termux/files/usr/bin/openclaude", 1.0f)
    )

    for (step in steps) {
        onProgress(step.name, step.progress, ">>> ${step.command.take(80)}...")
        val result = TermuxRunner.executeCommand(context, step.command, workspacePath)
        val output = if (result.stdout.isNotBlank()) result.stdout.trim().lines().lastOrNull() ?: "" else ""
        val error = result.stderr?.trim()?.lines()?.lastOrNull() ?: ""

        if (result.exitCode != 0 && error.isNotBlank() && !error.contains("already") && !error.contains("WARN")) {
            onProgress("Error: ${step.name}", step.progress, "ERR: $error")
        } else {
            onProgress(step.name, step.progress, if (output.isNotBlank()) output.take(120) else null)
        }
    }
}
