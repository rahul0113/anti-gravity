package com.antigravity.vibecoder.ui.view

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.data.Provider
import com.antigravity.vibecoder.ui.theme.*

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
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isSshPasswordVisible by remember { mutableStateOf(false) }
    var showSshSettings by remember { mutableStateOf(false) }
    var showOpenClaudeSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "Settings",
            color = TerminalWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        // === AI Provider Section ===
        SettingsSection(title = "AI Provider", icon = Icons.Filled.SmartToy) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Provider.entries.filter { it != Provider.OPENCLAUDE }.forEach { provider ->
                    ProviderCard(
                        provider = provider,
                        isSelected = baseUrl == provider.defaultBaseUrl && !config.executionMode.equals(ExecutionMode.OPENCLAUDE),
                        onSelect = {
                            onBaseUrlChange(provider.defaultBaseUrl)
                            onModelNameChange(provider.defaultModel)
                            onConfigChange(config.copy(executionMode = ExecutionMode.SANDBOX))
                        }
                    )
                }
            }
        }

        // === OpenClaude Server Section ===
        SettingsSection(
            title = "OpenClaude Server",
            icon = Icons.Filled.Terminal,
            subtitle = "Recommended - Full agent with tools"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Enable/disable toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Connect to OpenClaude", color = TerminalWhite, fontSize = 14.sp)
                        Text(
                            "Run AI agent locally via Termux",
                            color = TerminalWhite.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = config.executionMode == ExecutionMode.OPENCLAUDE,
                        onCheckedChange = { enabled ->
                            onConfigChange(
                                config.copy(
                                    executionMode = if (enabled) ExecutionMode.OPENCLAUDE else ExecutionMode.SANDBOX
                                )
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = TerminalGreen,
                            uncheckedTrackColor = Color.Gray
                        )
                    )
                }

                // Expanded settings
                AnimatedVisibility(visible = config.executionMode == ExecutionMode.OPENCLAUDE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsInput(
                            label = "Server Host",
                            value = config.host,
                            onValueChange = { onConfigChange(config.copy(host = it)) },
                            placeholder = "127.0.0.1"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsInput(
                                label = "HTTP Port",
                                value = config.port.toString(),
                                onValueChange = { onConfigChange(config.copy(port = it.toIntOrNull() ?: 8022)) },
                                placeholder = "8022",
                                modifier = Modifier.weight(1f)
                            )
                            SettingsInput(
                                label = "gRPC Port",
                                value = config.grpcPort.toString(),
                                onValueChange = { onConfigChange(config.copy(grpcPort = it.toIntOrNull() ?: 50051)) },
                                placeholder = "50051",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SettingsInput(
                            label = "Working Directory",
                            value = config.workspacePath,
                            onValueChange = { onConfigChange(config.copy(workspacePath = it)) },
                            placeholder = "/data/data/com.termux/files/home"
                        )
                    }
                }
            }
        }

        // === API Key Section (for non-OpenClaude providers) ===
        if (config.executionMode != ExecutionMode.OPENCLAUDE) {
            SettingsSection(title = "API Key", icon = Icons.Filled.Key) {
                SettingsInput(
                    label = "API Key",
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    placeholder = "sk-...",
                    isPassword = true,
                    isPasswordVisible = isApiKeyVisible,
                    onTogglePassword = { isApiKeyVisible = !isApiKeyVisible }
                )
                if (apiKey.isNotBlank()) {
                    Text(
                        text = "Key saved locally",
                        color = TerminalGreen.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // === Execution Mode Section ===
        SettingsSection(title = "Execution Mode", icon = Icons.Filled.PlayArrow) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExecutionMode.entries.forEach { mode ->
                    ExecutionModeOption(
                        mode = mode,
                        isSelected = config.executionMode == mode,
                        onSelect = { onConfigChange(config.copy(executionMode = mode)) }
                    )
                }
            }
        }

        // === Termux / SSH Section ===
        if (config.executionMode == ExecutionMode.TERMUX_SERVICE || config.executionMode == ExecutionMode.SSH) {
            SettingsSection(title = "Connection", icon = Icons.Filled.Link) {
                if (config.executionMode == ExecutionMode.SSH) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsInput(
                            label = "SSH Host",
                            value = config.host,
                            onValueChange = { onConfigChange(config.copy(host = it)) },
                            placeholder = "192.168.1.100"
                        )
                        SettingsInput(
                            label = "SSH Port",
                            value = config.port.toString(),
                            onValueChange = { onConfigChange(config.copy(port = it.toIntOrNull() ?: 22)) },
                            placeholder = "22"
                        )
                        SettingsInput(
                            label = "Username",
                            value = config.user,
                            onValueChange = { onConfigChange(config.copy(user = it)) },
                            placeholder = "android"
                        )
                        SettingsInput(
                            label = "Password / Key",
                            value = config.passwordKey,
                            onValueChange = { onConfigChange(config.copy(passwordKey = it)) },
                            placeholder = "Enter password or paste private key",
                            isPassword = true,
                            isPasswordVisible = isSshPasswordVisible,
                            onTogglePassword = { isSshPasswordVisible = !isSshPasswordVisible }
                        )
                    }
                } else {
                    Text(
                        text = "Uses Termux:API app. Install via: pkg install termux-services",
                        color = TerminalWhite.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = TerminalGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = TerminalWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TerminalWhite.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val bgColor = if (isSelected) TerminalGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
    val borderColor = if (isSelected) TerminalGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider icon dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (provider) {
                        Provider.OPENAI -> TerminalGreen
                        Provider.GEMINI -> TerminalCyan
                        Provider.DEEPSEEK -> TerminalAmber
                        Provider.GITHUB_MODELS -> TerminalWhite
                        Provider.OLLAMA -> TerminalGreen
                        Provider.OPENCLAUDE -> TerminalCyan
                    }
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                color = TerminalWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = provider.defaultModel,
                color = TerminalWhite.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = TerminalGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ExecutionModeOption(
    mode: ExecutionMode,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val (title, description) = when (mode) {
        ExecutionMode.OPENCLAUDE -> "OpenClaude" to "Full AI agent with terminal access"
        ExecutionMode.SANDBOX -> "Sandbox" to "AI writes code, you copy-paste"
        ExecutionMode.TERMUX_SERVICE -> "Termux Service" to "Direct command execution"
        ExecutionMode.SSH -> "SSH" to "Remote machine execution"
    }

    val bgColor = if (isSelected) TerminalGreen.copy(alpha = 0.12f) else Color.Transparent
    val borderColor = if (isSelected) TerminalGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = TerminalGreen,
                unselectedColor = TerminalWhite.copy(alpha = 0.4f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = description, color = TerminalWhite.copy(alpha = 0.45f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TerminalWhite.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TerminalWhite.copy(alpha = 0.3f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                if (isPassword && onTogglePassword != null) {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle password",
                            tint = TerminalWhite.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TerminalGreen.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                cursorColor = TerminalGreen,
                focusedTextColor = TerminalWhite,
                unfocusedTextColor = TerminalWhite
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )
    }
}
