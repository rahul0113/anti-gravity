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

private enum class SetupProvider(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val envVars: List<String>,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val keyPlaceholder: String
) {
    OPENAI("OpenAI", "GPT-4o, GPT-4.1, o3", Icons.Default.SmartToy, TerminalGreen,
        listOf("CLAUDE_CODE_USE_OPENAI=1", "OPENAI_API_KEY", "OPENAI_MODEL"),
        "https://api.openai.com/v1", "gpt-4o", "sk-..."),
    ANTHROPIC("Anthropic", "Claude Sonnet, Opus, Haiku", Icons.Default.Psychology, TerminalAmber,
        listOf("ANTHROPIC_API_KEY", "ANTHROPIC_MODEL"),
        "https://api.anthropic.com", "claude-sonnet-4-20250514", "sk-ant-..."),
    GEMINI("Gemini", "Gemini 2.5, 2.0 Flash", Icons.Default.AutoAwesome, TerminalCyan,
        listOf("GEMINI_API_KEY"),
        "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", "AI..."),
    DEEPSEEK("DeepSeek", "DeepSeek V3, R1", Icons.Default.Code, Color(0xFF4A9EFF),
        listOf("CLAUDE_CODE_USE_OPENAI=1", "OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL"),
        "https://api.deepseek.com/v1", "deepseek-chat", "sk-..."),
    GITHUB("GitHub Models", "GPT-4o, Llama via GitHub", Icons.Default.AccountCircle, TerminalWhite,
        listOf("CLAUDE_CODE_USE_GITHUB=1", "GITHUB_TOKEN"),
        "https://models.inference.ai.azure.com", "gpt-4o", "ghp_..."),
    OLLAMA("Ollama (Local)", "Llama, Qwen, DeepSeek locally", Icons.Default.PhoneAndroid, TerminalGreen,
        listOf("CLAUDE_CODE_USE_OPENAI=1", "OPENAI_BASE_URL", "OPENAI_MODEL"),
        "http://localhost:11434/v1", "qwen2.5-coder:7b", ""),
    ZEN("OpenCode Zen", "Pay-as-you-go gateway", Icons.Default.Bolt, Color(0xFF9C27B0),
        listOf("OPENCODE_API_KEY", "OPENAI_BASE_URL=https://opencode.ai/zen/v1", "OPENAI_MODEL=anthropic/claude-sonnet-4-20250514"),
        "https://opencode.ai/zen/v1", "anthropic/claude-sonnet-4-20250514", "")
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
    var step by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<SetupProvider?>(null) }
    var keyInput by remember { mutableStateOf(apiKey) }
    var installing by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var installed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) {
            TermuxRunner.executeCommand(context, "which openclaude 2>/dev/null || echo ''", config.workspacePath)
        }
        installed = r.stdout.trim().isNotEmpty()
        if (installed) step = 1
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Transparent)
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Step dots
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            StepDot(1, step >= 0, "Install"); StepLine(); StepDot(2, step >= 1, "Provider"); StepLine(); StepDot(3, step >= 2, "Done")
        }

        when (step) {
            0 -> Card {
                SectionHeader(Icons.Default.Download, "Install OpenClaude")
                Text("OpenClaude CLI for AI coding with 200+ models.", color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp)
                if (!installing) {
                    Button(onClick = {
                        installing = true; log = emptyList()
                        scope.launch {
                            listOf(
                                "pkg update -y && pkg install -y nodejs-lts git" to "Installing Node.js & git",
                                "npm install -g @gitlawb/openclaude@latest 2>&1" to "Installing OpenClaude CLI"
                            ).forEach { (cmd, desc) ->
                                log = log + ">>> $desc"
                                val r = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                                log = log + if (r.exitCode == 0) "  OK" else "  WARN: ${r.stderr?.take(80)}"
                            }
                            installing = false; installed = true; step = 1
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen), shape = RoundedCornerShape(10.dp)) {
                        Text("Install", fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = { step = 1 }) { Text("Skip", color = TerminalWhite.copy(alpha = 0.5f)) }
                } else {
                    Text("Installing...", color = TerminalGreen, fontSize = 13.sp)
                    Box(Modifier.fillMaxWidth().heightIn(max = 100.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)) {
                        Column { log.takeLast(6).forEach { Text(it, color = TerminalWhite.copy(alpha = 0.7f), fontSize = 10.sp) } }
                    }
                }
            }

            1 -> Card {
                SectionHeader(Icons.Default.Cloud, "Choose Provider")
                SetupProvider.entries.forEach { p ->
                    val sel = selected == p
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (sel) TerminalGreen.copy(alpha = 0.12f) else Color.Transparent)
                            .border(1.dp, if (sel) TerminalGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .clickable { selected = p }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(p.icon, null, tint = p.color, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.displayName, color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(p.description, color = TerminalWhite.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                        if (sel) Icon(Icons.Default.CheckCircle, null, tint = TerminalGreen, modifier = Modifier.size(16.dp))
                    }
                }

                AnimatedVisibility(visible = selected != null && selected!!.keyPlaceholder.isNotEmpty()) {
                    OutlinedTextField(
                        value = keyInput, onValueChange = { keyInput = it },
                        label = { Text("API Key") }, placeholder = { Text(selected?.keyPlaceholder ?: "") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = fieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = 0 }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Back", color = TerminalWhite.copy(alpha = 0.6f)) }
                    Button(onClick = {
                        selected?.let { p ->
                            onBaseUrlChange(p.defaultBaseUrl); onModelNameChange(p.defaultModel); onApiKeyChange(keyInput)
                            scope.launch { saveProfile(context, config.workspacePath, p, keyInput) }
                            step = 2
                        }
                    }, modifier = Modifier.weight(1f), enabled = selected != null,
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen, disabledContainerColor = Color.Gray.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(10.dp)) { Text("Continue", fontWeight = FontWeight.Medium) }
                }
            }

            2 -> Card {
                SectionHeader(Icons.Default.CheckCircle, "Setup Complete")
                Text("Provider: ${selected?.displayName}", color = TerminalGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Model: ${selected?.defaultModel}", color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp)

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Server", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Port ${config.port}", color = TerminalWhite.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                    Button(onClick = {
                        scope.launch { TermuxRunner.executeCommand(context, "openclaude serve --port ${config.port} &", config.workspacePath) }
                    }, colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)); Text("Start")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Port", config.port, { onConfigChange(config.copy(port = it)) }, Modifier.weight(1f))
                    NumberField("gRPC", config.grpcPort, { onConfigChange(config.copy(grpcPort = it)) }, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = config.workspacePath, onValueChange = { onConfigChange(config.copy(workspacePath = it)) },
                    label = { Text("Working Dir") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = fieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Reconfigure", color = TerminalWhite.copy(alpha = 0.6f), fontSize = 12.sp) }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TerminalGreen, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = TerminalWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun StepDot(n: Int, active: Boolean, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (active) TerminalGreen else Color.Gray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Text("$n", color = if (active) Color.Black else TerminalWhite.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = if (active) TerminalGreen else TerminalWhite.copy(alpha = 0.4f), fontSize = 10.sp)
    }
}

@Composable private fun StepLine() { Box(modifier = Modifier.width(40.dp).height(2.dp).padding(horizontal = 8.dp).background(TerminalWhite.copy(alpha = 0.15f))) }

@Composable private fun NumberField(label: String, value: Int, onValue: (Int) -> Unit, mod: Modifier = Modifier) {
    OutlinedTextField(value = value.toString(), onValueChange = { onValue(it.toIntOrNull() ?: value) },
        label = { Text(label) }, modifier = mod, singleLine = true, colors = fieldColors(), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TerminalGreen.copy(alpha = 0.5f), unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
    cursorColor = TerminalGreen, focusedTextColor = TerminalWhite, unfocusedTextColor = TerminalWhite,
    focusedLabelColor = TerminalGreen.copy(alpha = 0.7f), unfocusedLabelColor = TerminalWhite.copy(alpha = 0.4f))

private suspend fun saveProfile(ctx: android.content.Context, ws: String, p: SetupProvider, key: String) = withContext(Dispatchers.IO) {
    val exports = p.envVars.joinToString("\n") { env ->
        val (name, value) = if (env.contains("=")) { val parts = env.split("=", limit = 2); parts[0] to parts[1] } else env to key
        "export $name=\"$value\""
    }
    TermuxRunner.executeCommand(ctx, "cat >> ~/.bashrc << 'E'\n$exports\nE", ws)
    TermuxRunner.executeCommand(ctx, "mkdir -p ~/.openclaude && echo '{\"provider\":\"${p.name.lowercase()}\",\"model\":\"${p.defaultModel}\"}' > ~/.openclaude/.openclaude-profile.json", ws)
}
