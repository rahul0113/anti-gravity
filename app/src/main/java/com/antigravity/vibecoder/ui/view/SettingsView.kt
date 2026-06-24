package com.antigravity.vibecoder.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "> CONFIG_MANAGER.SH",
            color = TerminalGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // OpenClaude Section
        Card(
            modifier = Modifier.fillMaxWidth().glassPanel(shape = RoundedCornerShape(16.dp), alpha = 0.05f),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("--- OPENCLAUDE AGENT CONFIG ---", color = Color.White.copy(alpha=0.7f), fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)

                // OpenClaude gRPC Port
                OutlinedTextField(
                    value = config.grpcPort.toString(),
                    onValueChange = {
                        val port = it.toIntOrNull() ?: 50051
                        onConfigChange(config.copy(grpcPort = port))
                    },
                    label = { Text("OpenClaude gRPC Port", color = TerminalGray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerminalGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TerminalWhite,
                        unfocusedTextColor = TerminalWhite
                    )
                )

                Text(
                    text = "* OpenClaude runs as a gRPC server on localhost. Install it via: npm install -g @anthropic-ai/claude-code",
                    color = TerminalAmber,
                    fontSize = 11.sp
                )

                Text(
                    text = "* Start the server: claude --grpc-port ${config.grpcPort}",
                    color = TerminalAmber,
                    fontSize = 11.sp
                )
            }
        }

        // API / Provider Section
        Card(
            modifier = Modifier.fillMaxWidth().glassPanel(shape = RoundedCornerShape(16.dp), alpha = 0.05f),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("--- AI PROVIDER CONFIG ---", color = Color.White.copy(alpha=0.7f), fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)

                // Provider Selection
                Text("Select Provider:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Provider.entries.filter { it != Provider.OPENCLAUDE }.forEach { provider ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBaseUrlChange(provider.defaultBaseUrl)
                                    onModelNameChange(provider.defaultModel)
                                }
                        ) {
                            RadioButton(
                                selected = baseUrl == provider.defaultBaseUrl,
                                onClick = {
                                    onBaseUrlChange(provider.defaultBaseUrl)
                                    onModelNameChange(provider.defaultModel)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = TerminalGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(provider.displayName, color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(provider.defaultModel, color = TerminalGray, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // API Key (skip for Ollama)
                if (!baseUrl.contains("localhost")) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text("API Key", color = TerminalGray) },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle API Key visibility",
                                    tint = TerminalGreen
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TerminalGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TerminalWhite,
                            unfocusedTextColor = TerminalWhite
                        )
                    )
                }

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Base URL", color = TerminalGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerminalGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TerminalWhite,
                        unfocusedTextColor = TerminalWhite
                    )
                )

                // Model
                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelNameChange,
                    label = { Text("Model Name", color = TerminalGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha=0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha=0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )
            }
        }

        // Connection Section
        Card(
            modifier = Modifier.fillMaxWidth().glassPanel(shape = RoundedCornerShape(16.dp), alpha = 0.05f),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("--- ENVIRONMENT RUNTIME ---", color = Color.White.copy(alpha=0.7f), fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)

                Text("Select Execution Mode:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // OpenClaude Option (NEW - Primary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfigChange(config.copy(executionMode = ExecutionMode.OPENCLAUDE)) }
                    ) {
                        RadioButton(
                            selected = config.executionMode == ExecutionMode.OPENCLAUDE,
                            onClick = { onConfigChange(config.copy(executionMode = ExecutionMode.OPENCLAUDE)) },
                            colors = RadioButtonDefaults.colors(selectedColor = TerminalGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("OpenClaude Agent (Recommended)", color = TerminalGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Full agent with tool calling, file editing, bash, and 200+ model support.", color = TerminalGray, fontSize = 11.sp)
                        }
                    }

                    // Sandbox Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfigChange(config.copy(executionMode = ExecutionMode.SANDBOX)) }
                    ) {
                        RadioButton(
                            selected = config.executionMode == ExecutionMode.SANDBOX,
                            onClick = { onConfigChange(config.copy(executionMode = ExecutionMode.SANDBOX)) },
                            colors = RadioButtonDefaults.colors(selectedColor = TerminalGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("Local Sandbox", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Simple chat via API. No file tools, no agent capabilities.", color = TerminalGray, fontSize = 11.sp)
                        }
                    }

                    // Termux Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfigChange(config.copy(executionMode = ExecutionMode.TERMUX_SERVICE)) }
                    ) {
                        RadioButton(
                            selected = config.executionMode == ExecutionMode.TERMUX_SERVICE,
                            onClick = { onConfigChange(config.copy(executionMode = ExecutionMode.TERMUX_SERVICE)) },
                            colors = RadioButtonDefaults.colors(selectedColor = TerminalGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("Direct Termux Service", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Runs CLI via Termux IPC. Requires opencode CLI installed.", color = TerminalGray, fontSize = 11.sp)
                        }
                    }

                    // SSH Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfigChange(config.copy(executionMode = ExecutionMode.SSH)) }
                    ) {
                        RadioButton(
                            selected = config.executionMode == ExecutionMode.SSH,
                            onClick = { onConfigChange(config.copy(executionMode = ExecutionMode.SSH)) },
                            colors = RadioButtonDefaults.colors(selectedColor = TerminalGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("SSH Server Terminal", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Connects over SSH to remote dev boxes or local termux sshd.", color = TerminalGray, fontSize = 11.sp)
                        }
                    }
                }

                if (config.executionMode == ExecutionMode.SSH) {
                    OutlinedTextField(
                        value = config.host,
                        onValueChange = { onConfigChange(config.copy(host = it)) },
                        label = { Text("SSH Host", color = TerminalGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreen, unfocusedBorderColor = DarkBorder)
                    )

                    OutlinedTextField(
                        value = config.port.toString(),
                        onValueChange = {
                            val portVal = it.toIntOrNull() ?: 22
                            onConfigChange(config.copy(port = portVal))
                        },
                        label = { Text("SSH Port", color = TerminalGray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreen, unfocusedBorderColor = DarkBorder)
                    )

                    OutlinedTextField(
                        value = config.user,
                        onValueChange = { onConfigChange(config.copy(user = it)) },
                        label = { Text("SSH Username", color = TerminalGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreen, unfocusedBorderColor = DarkBorder)
                    )

                    OutlinedTextField(
                        value = config.passwordKey,
                        onValueChange = { onConfigChange(config.copy(passwordKey = it)) },
                        label = { Text("SSH Password / Private Key Data", color = TerminalGray) },
                        visualTransformation = if (isSshPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isSshPasswordVisible = !isSshPasswordVisible }) {
                                Icon(
                                    imageVector = if (isSshPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = TerminalGreen
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreen, unfocusedBorderColor = DarkBorder)
                    )
                }

                OutlinedTextField(
                    value = config.workspacePath,
                    onValueChange = { onConfigChange(config.copy(workspacePath = it)) },
                    label = { Text("Workspace Path Directory", color = TerminalGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha=0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha=0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )

                Text(
                    text = when (config.executionMode) {
                        ExecutionMode.OPENCLAUDE -> "* Routes through OpenClaude gRPC agent on port ${config.grpcPort}. Supports file tools, bash, grep, and 200+ models."
                        ExecutionMode.TERMUX_SERVICE -> "* Runs direct CLI sessions using Termux RUN_COMMAND intents."
                        ExecutionMode.SSH -> "* Uses SSH credentials to connect to a remote server."
                        ExecutionMode.SANDBOX -> "* Simple API chat only. No agent tools."
                    },
                    color = TerminalAmber,
                    fontSize = 11.sp
                )
            }
        }
    }
}
