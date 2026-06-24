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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.data.AnsiParser
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
    val isError: Boolean = false,
    val ansiOutput: String = ""
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
    onOpenDrawer: () -> Unit,
    config: ConnectionConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by rememberSaveable { mutableStateOf("terminal") } // "files" or "terminal"

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
            Spacer(modifier = Modifier.width(4.dp))

            // Screen tabs
            ScreenTab("Terminal", Icons.Filled.Code, currentScreen == "terminal") { currentScreen = "terminal" }
            Spacer(modifier = Modifier.width(4.dp))
            ScreenTab("Files", Icons.Filled.Folder, currentScreen == "files") { currentScreen = "files" }
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Anti-Gravity",
                color = TerminalGreen.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        HorizontalDivider(color = TerminalWhite.copy(alpha = 0.08f), thickness = 1.dp)

        when (currentScreen) {
            "terminal" -> TermuxTerminal(
                config = config,
                modifier = Modifier.fillMaxSize()
            )
            "files" -> FileManager(
                config = config,
                onFileOpen = { /* TODO: open in editor */ },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TermuxTerminal(
    config: ConnectionConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var terminalLines by remember { mutableStateOf<List<TerminalLine>>(emptyList()) }
    var terminalInput by remember { mutableStateOf(TextFieldValue("")) }
    var terminalHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var isRunning by remember { mutableStateOf(false) }
    var currentPath by rememberSaveable { mutableStateOf(config.workspacePath) }

    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.4f))) {
        // Terminal header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Code, null, tint = TerminalGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Termux", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                currentPath.removePrefix(config.workspacePath).ifEmpty { "~" },
                color = TerminalGreen.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        HorizontalDivider(color = TerminalWhite.copy(alpha = 0.06f), thickness = 1.dp)

        // Terminal output
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            state = listState
        ) {
            // Welcome message
            if (terminalLines.isEmpty()) {
                item {
                    Text(
                        text = "Anti-Gravity Terminal v1.0\nType 'help' for available commands.\n",
                        color = TerminalGreen.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(terminalLines) { line ->
                // Prompt
                if (line.prompt.isNotEmpty()) {
                    val promptText = buildAnnotatedString {
                        withStyle(SpanStyle(color = TerminalGreen, fontWeight = FontWeight.Bold)) {
                            append(line.prompt)
                        }
                    }
                    Text(promptText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }

                // Output with ANSI colors
                if (line.ansiOutput.isNotEmpty() || line.output.isNotEmpty()) {
                    val displayText = if (line.ansiOutput.isNotEmpty()) line.ansiOutput else line.output
                    val spans = AnsiParser.parse(displayText)

                    if (spans.any { it.color != Color.Unspecified || it.background != Color.Unspecified }) {
                        // Has ANSI colors — render with styled text
                        val annotatedString = buildAnnotatedString {
                            spans.forEach { span ->
                                val style = SpanStyle(
                                    color = if (span.color != Color.Unspecified) span.color else {
                                        if (line.isError) TerminalRed else TerminalWhite.copy(alpha = 0.85f)
                                    },
                                    fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal
                                )
                                withStyle(style) { append(span.text) }
                            }
                        }
                        Text(annotatedString, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        // No ANSI colors — plain text
                        Text(
                            displayText,
                            color = if (line.isError) TerminalRed else TerminalWhite.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Auto-scroll
            if (listState.layoutInfo.totalItemsCount > 0) {
                item {
                    LaunchedEffect(listState.layoutInfo.totalItemsCount) {
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                }
            }
        }

        // Input line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = TerminalGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = terminalInput,
                onValueChange = { terminalInput = it },
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
                        val cmd = terminalInput.text
                        if (cmd.isNotBlank()) {
                            terminalLines = terminalLines + TerminalLine("$ ", cmd)
                            terminalHistory = terminalHistory + cmd
                            historyIndex = -1
                            isRunning = true
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
                                    terminalLines = terminalLines + TerminalLine(
                                        prompt = "",
                                        output = output,
                                        isError = result.exitCode != 0,
                                        ansiOutput = output
                                    )
                                }

                                // Handle cd
                                if (cmd.trimStart().startsWith("cd ")) {
                                    val target = cmd.trimStart().removePrefix("cd ").trim()
                                    currentPath = when {
                                        target.startsWith("/") -> target
                                        target == "~" -> config.workspacePath
                                        currentPath.endsWith("/") -> currentPath + target
                                        else -> "$currentPath/$target"
                                    }
                                }

                                isRunning = false
                            }
                        }
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
private fun FileManager(
    config: ConnectionConfig,
    onFileOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPath by rememberSaveable { mutableStateOf(config.workspacePath) }
    var files by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    Column(modifier = modifier) {
        // Breadcrumb
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPath != "/") {
                Icon(Icons.Filled.ArrowUpward, "Up", tint = TerminalCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentPath.removePrefix("/"),
                    color = TerminalWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    maxLines = 1
                )
            }
        }

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerminalGreen, modifier = Modifier.size(32.dp))
            }
            errorMessage != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMessage ?: "", color = TerminalRed)
            }
            else -> {
                // Go up button
                if (currentPath != "/") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentPath = currentPath.substringBeforeLast("/").ifEmpty { "/" } }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FolderOpen, "Up", tint = TerminalCyan, modifier = Modifier.size(18.dp))
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
                                        currentPath = if (currentPath.endsWith("/")) currentPath + file.name else "$currentPath/${file.name}"
                                    } else {
                                        onFileOpen("$currentPath/${file.name}")
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                null,
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
    }
}

@Composable
private fun ScreenTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) TerminalGreen.copy(alpha = 0.2f) else Color.Transparent
    val textColor = if (isSelected) TerminalGreen else TerminalWhite.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = textColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
