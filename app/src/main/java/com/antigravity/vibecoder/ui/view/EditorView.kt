package com.antigravity.vibecoder.ui.view

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.data.TermuxRunner
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.WorkspaceFile
import com.antigravity.vibecoder.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TerminalLine(
    val prompt: String,
    val output: String,
    val isError: Boolean = false
)

private fun parseLsLaOutput(output: String): List<WorkspaceFile> {
    return output.lines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size >= 8) {
            val permissions = parts[0]
            val name = parts.drop(7).joinToString(" ")
            if (name != "." && name != "..") {
                val isDirectory = permissions.startsWith("d")
                val size = parts[4].toLongOrNull() ?: 0L
                WorkspaceFile(name = name, path = name, isDirectory = isDirectory, size = size)
            } else null
        } else null
    }
}

@Composable
fun EditorView(
    messages: List<com.antigravity.vibecoder.model.ChatMessage>,
    isProcessing: Boolean,
    onSendPrompt: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    config: ConnectionConfig,
    apiKey: String,
    baseUrl: String,
    modelName: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentPath by rememberSaveable { mutableStateOf(config.workspacePath) }
    var files by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var viewMode by rememberSaveable { mutableStateOf("terminal") }

    var terminalLines by remember { mutableStateOf<List<TerminalLine>>(emptyList()) }
    var terminalInput by remember { mutableStateOf(TextFieldValue("")) }
    var terminalHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var isTerminalRunning by remember { mutableStateOf(false) }

    // Load files when path changes — always uses TermuxRunner (local terminal)
    LaunchedEffect(currentPath) {
        isLoading = true
        errorMessage = null
        try {
            files = withContext(Dispatchers.IO) {
                val result = TermuxRunner.executeCommand(context, "ls -la \"$currentPath\"", config.workspacePath)
                if (result.error == null) parseLsLaOutput(result.stdout) else {
                    errorMessage = result.error
                    emptyList()
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Menu, "Menu", tint = TerminalWhite, modifier = Modifier.size(20.dp))
            }

            Text(
                text = currentPath.removePrefix("/"),
                color = TerminalWhite.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                maxLines = 1
            )

            ViewModeButton("Files", Icons.Filled.Folder, viewMode == "files") { viewMode = "files" }
            ViewModeButton("Editor", Icons.Filled.Edit, viewMode == "editor") { viewMode = "editor" }
            ViewModeButton("Terminal", Icons.Filled.Code, viewMode == "terminal") { viewMode = "terminal" }
        }

        HorizontalDivider(color = TerminalWhite.copy(alpha = 0.1f), thickness = 1.dp)

        when (viewMode) {
            "files" -> {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TerminalGreen, modifier = Modifier.size(32.dp))
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage ?: "", color = TerminalRed)
                    }
                } else {
                    if (currentPath != "/") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentPath = currentPath.substringBeforeLast("/").ifEmpty { "/" } }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ArrowUpward, "Up", tint = TerminalCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("..", color = TerminalCyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(files) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (file.isDirectory) {
                                            currentPath = if (currentPath.endsWith("/")) currentPath + file.name
                                            else "$currentPath/${file.name}"
                                        } else {
                                            selectedFile = file
                                            coroutineScope.launch {
                                                try {
                                                    fileContent = withContext(Dispatchers.IO) {
                                                        val result = TermuxRunner.executeCommand(
                                                            context, "cat \"$currentPath/${file.name}\"", config.workspacePath
                                                        )
                                                        result.stdout
                                                    }
                                                    viewMode = "editor"
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (file.isDirectory) TerminalAmber else TerminalCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, color = TerminalWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                    if (!file.isDirectory && file.size > 0) {
                                        Text(formatFileSize(file.size), color = TerminalWhite.copy(alpha = 0.4f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "editor" -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.InsertDriveFile, null, tint = TerminalCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedFile?.name ?: "No file", color = TerminalWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.weight(1f))
                        if (selectedFile != null && fileContent.isNotBlank()) {
                            TextButton(onClick = {
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            TermuxRunner.executeCommand(
                                                context,
                                                "cat > \"$currentPath/${selectedFile!!.name}\" << 'ENDOFFILE'\n$fileContent\nENDOFFILE",
                                                config.workspacePath
                                            )
                                        }
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Text("Save", color = TerminalGreen, fontSize = 12.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = TerminalWhite.copy(alpha = 0.1f), thickness = 1.dp)
                    BasicTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = TerminalWhite,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(TerminalGreen)
                    )
                }
            }

            "terminal" -> {
                // Terminal ALWAYS uses TermuxRunner — independent of AI mode
                TerminalPanel(
                    lines = terminalLines,
                    input = terminalInput,
                    onInputChange = { terminalInput = it },
                    isRunning = isTerminalRunning,
                    onExecute = { cmd ->
                        terminalLines = terminalLines + TerminalLine("$ ", cmd)
                        terminalHistory = terminalHistory + cmd
                        historyIndex = -1
                        isTerminalRunning = true
                        terminalInput = TextFieldValue("")

                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                TermuxRunner.executeCommand(context, cmd, currentPath)
                            }

                            val output = buildString {
                                if (result.stdout.isNotBlank()) append(result.stdout)
                                if (result.stderr.isNotBlank()) {
                                    if (isNotBlank()) append("\n")
                                    append(result.stderr)
                                }
                            }.trimEnd()

                            if (output.isNotBlank()) {
                                terminalLines = terminalLines + TerminalLine("", output, result.exitCode != 0)
                            }

                            if (cmd.trimStart().startsWith("cd ")) {
                                val target = cmd.trimStart().removePrefix("cd ").trim()
                                val newPath = when {
                                    target.startsWith("/") -> target
                                    target == "~" -> config.workspacePath
                                    currentPath.endsWith("/") -> currentPath + target
                                    else -> "$currentPath/$target"
                                }
                                currentPath = newPath
                            }

                            isTerminalRunning = false
                        }
                    },
                    onClear = { terminalLines = emptyList() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TerminalPanel(
    lines: List<TerminalLine>,
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    isRunning: Boolean,
    onExecute: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Code, null, tint = TerminalGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Terminal", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Text("Termux", color = TerminalGreen.copy(alpha = 0.7f), fontSize = 10.sp)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.ClearAll, "Clear", tint = TerminalWhite.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }

        HorizontalDivider(color = TerminalWhite.copy(alpha = 0.08f), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            state = rememberLazyListState()
        ) {
            items(lines) { line ->
                if (line.prompt.isNotEmpty()) {
                    Text(line.prompt, color = TerminalGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                if (line.output.isNotEmpty()) {
                    Text(
                        line.output,
                        color = if (line.isError) TerminalRed else TerminalWhite.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = TerminalGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(TerminalGreen),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        val cmd = input.text
                        if (cmd.isNotBlank()) onExecute(cmd)
                        focusManager.clearFocus()
                    }
                )
            )
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TerminalGreen, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) TerminalGreen.copy(alpha = 0.2f) else Color.Transparent
    val textColor = if (isSelected) TerminalGreen else TerminalWhite.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = textColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
