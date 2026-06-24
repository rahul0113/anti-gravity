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
    val prompt: String = "",
    val output: String = "",
    val isError: Boolean = false
)

private fun parseLsOutput(output: String): List<WorkspaceFile> {
    return output.lines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size >= 8) {
            val isDir = parts[0].startsWith("d")
            val name = parts.drop(7).joinToString(" ")
            if (name != "." && name != "..") {
                WorkspaceFile(name = name, path = name, isDirectory = isDir, size = parts[4].toLongOrNull() ?: 0L)
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
    var currentScreen by rememberSaveable { mutableStateOf("terminal") }

    Column(modifier = modifier.background(Color.Transparent)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Menu, "Menu", tint = TerminalWhite, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TabButton("Terminal", currentScreen == "terminal") { currentScreen = "terminal" }
            Spacer(modifier = Modifier.width(4.dp))
            TabButton("Files", currentScreen == "files") { currentScreen = "files" }
        }

        HorizontalDivider(color = TerminalWhite.copy(alpha = 0.08f), thickness = 1.dp)

        when (currentScreen) {
            "terminal" -> TerminalPanel(config = config, modifier = Modifier.fillMaxSize())
            "files" -> FilePanel(config = config, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) TerminalGreen.copy(alpha = 0.2f) else Color.Transparent
    val fg = if (selected) TerminalGreen else TerminalWhite.copy(alpha = 0.5f)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TerminalPanel(config: ConnectionConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var lines by remember { mutableStateOf<List<TerminalLine>>(emptyList()) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var path by rememberSaveable { mutableStateOf(config.workspacePath) }
    val listState = rememberLazyListState()

    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.4f))) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Code, null, tint = TerminalGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Terminal", color = TerminalWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                path.removePrefix(config.workspacePath).ifEmpty { "~" },
                color = TerminalGreen.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
            )
        }
        HorizontalDivider(color = TerminalWhite.copy(alpha = 0.06f), thickness = 1.dp)

        // Output
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), state = listState) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        "Anti-Gravity Terminal\nType 'help' for commands.\n",
                        color = TerminalGreen.copy(alpha = 0.5f), fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            items(lines) { line ->
                if (line.prompt.isNotEmpty()) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = TerminalGreen, fontWeight = FontWeight.Bold)) { append(line.prompt) }
                        }, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
                if (line.output.isNotEmpty()) {
                    val spans = AnsiParser.parse(line.output)
                    if (spans.any { it.color != Color.Unspecified }) {
                        val text = buildAnnotatedString {
                            spans.forEach { span ->
                                withStyle(SpanStyle(
                                    color = if (span.color != Color.Unspecified) span.color else TerminalWhite.copy(alpha = 0.85f),
                                    fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal
                                )) { append(span.text) }
                            }
                        }
                        Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        Text(
                            line.output,
                            color = if (line.isError) TerminalRed else TerminalWhite.copy(alpha = 0.85f),
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            if (listState.layoutInfo.totalItemsCount > 0) {
                item {
                    LaunchedEffect(listState.layoutInfo.totalItemsCount) {
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                }
            }
        }

        // Input
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = TerminalGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = input, onValueChange = { newValue -> input = newValue },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                textStyle = LocalTextStyle.current.copy(color = TerminalWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(TerminalGreen), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val cmd = input.text
                    if (cmd.isNotBlank()) {
                        lines = lines + TerminalLine(prompt = "$ ", output = cmd)
                        history = history + cmd
                        running = true
                        input = TextFieldValue("")
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                TermuxRunner.executeCommand(context, cmd, path)
                            }
                            val output = buildString {
                                if (result.stdout.isNotBlank()) append(result.stdout)
                                if (result.stderr.isNotBlank()) {
                                    if (isNotBlank()) append("\n")
                                    append(result.stderr)
                                }
                            }.trimEnd()
                            if (output.isNotBlank()) {
                                lines = lines + TerminalLine(output = output, isError = result.exitCode != 0)
                            }
                            if (cmd.trimStart().startsWith("cd ")) {
                                val target = cmd.trimStart().removePrefix("cd ").trim()
                                path = when {
                                    target.startsWith("/") -> target
                                    target == "~" -> config.workspacePath
                                    path.endsWith("/") -> path + target
                                    else -> "$path/$target"
                                }
                            }
                            running = false
                        }
                    }
                    focusManager.clearFocus()
                })
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TerminalGreen, strokeWidth = 2.dp)
            } else {
                // spacer when not running
            }
        }
    }
}

@Composable
private fun FilePanel(config: ConnectionConfig, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var path by rememberSaveable { mutableStateOf(config.workspacePath) }
    var files by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        loading = true; error = null
        try {
            files = withContext(Dispatchers.IO) {
                val r = TermuxRunner.executeCommand(context, "ls -la \"$path\"", config.workspacePath)
                if (r.error == null) parseLsOutput(r.stdout) else { error = r.error; emptyList() }
            }
        } catch (e: Exception) { error = e.message }
        loading = false
    }

    Column(modifier = modifier) {
        // Breadcrumb
        if (path != "/") {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { path = path.substringBeforeLast("/").ifEmpty { "/" } }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOpen, "Up", tint = TerminalCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("..", color = TerminalCyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    path.removePrefix("/"), color = TerminalWhite.copy(alpha = 0.5f), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerminalGreen, modifier = Modifier.size(32.dp))
            }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = TerminalRed)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(files) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (file.isDirectory) {
                                path = if (path.endsWith("/")) path + file.name else "$path/${file.name}"
                            }
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            null, tint = if (file.isDirectory) TerminalAmber else TerminalCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = TerminalWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            if (!file.isDirectory && file.size > 0) {
                                Text(formatSize(file.size), color = TerminalWhite.copy(alpha = 0.4f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(b: Long) = when {
    b < 1024 -> "$b B"
    b < 1048576 -> "${b / 1024} KB"
    b < 1073741824 -> "${b / 1048576} MB"
    else -> "${b / 1073741824} GB"
}
