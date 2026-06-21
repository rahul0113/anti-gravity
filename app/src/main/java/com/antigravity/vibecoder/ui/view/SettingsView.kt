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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
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
            .background(DarkBackground)
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

        // API Section
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("--- OPENCODE ZEN API CONFIG ---", color = TerminalCyan, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("Zen API Key", color = TerminalGray) },
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

                var expanded by remember { mutableStateOf(false) }
                var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
                var isLoadingModels by remember { mutableStateOf(false) }

                LaunchedEffect(apiKey, baseUrl) {
                    if (apiKey.isNotEmpty() && baseUrl.isNotEmpty()) {
                        isLoadingModels = true
                        val result = com.antigravity.vibecoder.data.ZenApiClient.getAvailableModels(apiKey, baseUrl)
                        result.onSuccess { models ->
                            availableModels = models
                        }
                        isLoadingModels = false
                    }
                }

                @OptIn(ExperimentalMaterial3Api::class)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = onModelNameChange,
                        label = { Text(if (isLoadingModels) "Fetching models..." else "Model Name", color = TerminalGray) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TerminalGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TerminalWhite,
                            unfocusedTextColor = TerminalWhite
                        )
                    )
                    
                    if (availableModels.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(DarkSurface).border(1.dp, DarkBorder, RoundedCornerShape(4.dp))
                        ) {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model, color = TerminalWhite) },
                                    onClick = {
                                        onModelNameChange(model)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Connection Section
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("--- ENVIRONMENT RUNTIME ---", color = TerminalCyan, fontWeight = FontWeight.Bold)

                Text("Select Execution Mode:", color = TerminalWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("Secure, runs within the app's internal sandbox storage.", color = TerminalGray, fontSize = 11.sp)
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
                            Text("Direct Termux Service (Fastest)", color = TerminalGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Natively links to Termux via background IPC service (zero-delay).", color = TerminalGray, fontSize = 11.sp)
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
                            Text("Connects over SSH socket (for remote dev boxes or local termux sshd).", color = TerminalGray, fontSize = 11.sp)
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
                        focusedBorderColor = TerminalGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TerminalWhite,
                        unfocusedTextColor = TerminalWhite
                    )
                )
                
                Text(
                    text = when (config.executionMode) {
                        ExecutionMode.TERMUX_SERVICE -> "* Runs direct CLI sessions using Termux RUN_COMMAND intents."
                        ExecutionMode.SSH -> "* Uses SSH credentials to connect to Termux's local sshd server or remote server."
                        ExecutionMode.SANDBOX -> "* Runs within the app's internal private sandbox space (/data/data/com.antigravity.vibecoder/files/workspace)."
                    },
                    color = TerminalAmber,
                    fontSize = 11.sp
                )
            }
        }
    }
}
