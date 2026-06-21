package com.antigravity.vibecoder.ui.view

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.data.SshConnection
import com.antigravity.vibecoder.data.TermuxRunner
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.MessageType
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.WorkspaceFile
import com.antigravity.vibecoder.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

private fun String.shellSingleQuote(): String = "'" + this.replace("'", "'\\''") + "'"

@Composable
fun EditorView(
    messages: List<ChatMessage>,
    isProcessing: Boolean,
    config: ConnectionConfig,
    onSendPrompt: (String) -> Unit,
    onClearConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fileList by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }

    // B-6 FIX: rememberSaveable preserves editor state across recompositions and config changes
    var openFiles by rememberSaveable { mutableStateOf<List<WorkspaceFile>>(emptyList()) }
    var currentFile by rememberSaveable { mutableStateOf<WorkspaceFile?>(null) }
    var fileContents by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isSaving by remember { mutableStateOf(false) }
    var isExplorerExpanded by remember { mutableStateOf(true) }

    val localWorkspace = remember { File(context.filesDir, "workspace") }
    val clipboardManager = LocalClipboardManager.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
                if (!spokenText.isNullOrEmpty()) onSendPrompt(spokenText)
            } catch (e: Throwable) {
                // Ignore speech recognition intent data unpacking errors
            }
        }
    }

    fun parseTermuxLs(stdout: String, basePath: String): List<WorkspaceFile> {
        return stdout.lines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size >= 3) {
                val name = parts[0].trim()
                if (name.isEmpty() || name == "*") return@mapNotNull null
                val size = parts[1].trim().toLongOrNull() ?: 0L
                val isDir = parts[2].trim() == "d"
                val fullPath = if (basePath.endsWith("/")) "$basePath$name" else "$basePath/$name"
                WorkspaceFile(name, fullPath, isDir, size)
            } else null
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    var reloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun reloadFiles() {
        reloadJob?.cancel()
        reloadJob = coroutineScope.launch {
            fileList = when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    val path = config.workspacePath.shellSingleQuote()
                    val cmd = "cd $path && for f in *; do [ -d \"\$f\" ] && echo -e \"\$f\\t0\\td\" || echo -e \"\$f\\t\$(stat -c%s \"\$f\")\\tf\"; done"
                    val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                    if (res.error == null) parseTermuxLs(res.stdout, config.workspacePath) else emptyList()
                }
                ExecutionMode.SSH -> SshConnection.listDirectory(config, config.workspacePath)
                ExecutionMode.SANDBOX -> {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            localWorkspace.mkdirs()
                            localWorkspace.listFiles()
                                ?.map { WorkspaceFile(it.name, it.absolutePath, it.isDirectory, it.length()) }
                                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                                ?: emptyList()
                        }
                    } catch (e: Throwable) {
                        emptyList()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { reloadFiles() }

    // C-1 FIX: Single shared scrollState on the outer Box — no nested scroll conflict
    val editorScrollState = rememberScrollState()
    val terminalListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            terminalListState.animateScrollToItem(messages.size - 1)
        }
    }

    Row(modifier = modifier.fillMaxSize().background(Color.Transparent).padding(4.dp)) {
        // File Explorer Sidebar
        if (isExplorerExpanded) {
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
                    .glassPanel(shape = RoundedCornerShape(12.dp), alpha = 0.05f)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FILES", color = TerminalGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { reloadFiles() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TerminalGreen, modifier = Modifier.size(16.dp))
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    items(fileList, key = { it.path }) { wFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!wFile.isDirectory) {
                                        if (!openFiles.contains(wFile)) {
                                            openFiles = openFiles + wFile
                                        }
                                        currentFile = wFile
                                        
                                        if (!fileContents.containsKey(wFile.path)) {
                                            coroutineScope.launch {
                                                val content = when (config.executionMode) {
                                                    ExecutionMode.TERMUX_SERVICE -> {
                                                        if (wFile.size > 250_000) {
                                                            "// ERROR: File is too large (${wFile.size / 1024} KB).\n// Termux IPC intent transactions are limited to ~250KB."
                                                        } else {
                                                            val res = TermuxRunner.executeCommand(
                                                                context, "cat -- ${wFile.path.shellSingleQuote()}", config.workspacePath
                                                            )
                                                            if (res.error == null) res.stdout else "Error: ${res.error}"
                                                        }
                                                    }
                                                    ExecutionMode.SSH -> SshConnection.readFile(config, wFile.path)
                                                    ExecutionMode.SANDBOX -> try {
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                            File(wFile.path).readText()
                                                        }
                                                    } catch (e: Exception) { "Failed to read: ${e.message}" }
                                                }
                                                fileContents = fileContents + (wFile.path to content)
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (wFile.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (wFile.isDirectory) TerminalCyan else TerminalGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = wFile.name,
                                color = if (currentFile?.path == wFile.path) TerminalGreen else TerminalWhite,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Editor Pane
        Column(
            modifier = Modifier
                .weight(if (isExplorerExpanded) 0.55f else 0.90f)
                .fillMaxHeight()
                .padding(end = 4.dp)
        ) {
            // Toolbar & Tabs
            Column(modifier = Modifier.fillMaxWidth().glassPanel(shape = RoundedCornerShape(12.dp), alpha = 0.08f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isExplorerExpanded = !isExplorerExpanded }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isExplorerExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Toggle Sidebar", tint = TerminalGreen, modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (currentFile != null) {
                        Button(
                            onClick = {
                                val path = currentFile?.path ?: return@Button
                                val content = fileContents[path] ?: return@Button
                                isSaving = true
                                coroutineScope.launch {
                                    val success = when (config.executionMode) {
                                        ExecutionMode.TERMUX_SERVICE -> {
                                            if (content.length > 250_000) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    android.widget.Toast.makeText(context, "File too large (>250KB) for Termux IPC. Use SSH mode.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                                false
                                            } else {
                                                val b64 = android.util.Base64.encodeToString(
                                                    content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
                                                )
                                                val qPath = path.shellSingleQuote()
                                                val cmd = "mkdir -p \$(dirname $qPath) && printf '%s' '$b64' | base64 -d > $qPath"
                                                val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                                                res.error == null && res.exitCode == 0
                                            }
                                        }
                                        ExecutionMode.SSH -> SshConnection.writeFile(config, path, content)
                                        ExecutionMode.SANDBOX -> try { 
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                File(path).writeText(content)
                                            }
                                            true 
                                        } catch (e: Exception) { false }
                                    }
                                    isSaving = false
                                    if (success) reloadFiles()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TerminalGreenDim),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(if (isSaving) "SAVING..." else "SAVE", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // Tabs Row
                if (openFiles.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().background(Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(openFiles) { file ->
                            val isSelected = currentFile?.path == file.path
                            Row(
                                modifier = Modifier
                                    .background(if (isSelected) Color.White.copy(alpha=0.1f) else Color.Transparent)
                                    .clickable { currentFile = file }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(file.name, color = if (isSelected) TerminalWhite else TerminalGray, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Close, 
                                    contentDescription = "Close Tab", 
                                    tint = TerminalGray, 
                                    modifier = Modifier.size(14.dp).clickable {
                                        openFiles = openFiles.filter { it.path != file.path }
                                        if (currentFile?.path == file.path) {
                                            currentFile = openFiles.lastOrNull()
                                        }
                                    }
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha=0.1f)))
                        }
                    }
                }
            }

            if (currentFile == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("SELECT A FILE TO VIBE EDIT", color = TerminalGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                // Editor code view (Top part)
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxWidth()
                        .verticalScroll(editorScrollState)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val content = fileContents[currentFile?.path] ?: ""
                        val lineCount = remember(content) { minOf(content.count { it == '\n' } + 1, 9999) }
                        val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

                        Text(
                            text = lineNumbers,
                            color = TerminalGreenDim.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .background(Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )

                        BasicTextField(
                            value = content,
                            onValueChange = { newText ->
                                currentFile?.let { f ->
                                    fileContents = fileContents + (f.path to newText)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TerminalWhite
                            )
                        )
                    }
                }
            }

            // Raw Terminal Output (Bottom part)
            Column(
                modifier = Modifier
                    .weight(if (currentFile == null) 1f else 0.3f)
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .glassPanel(shape = RoundedCornerShape(12.dp), alpha = 0.05f)
            ) {
                // Terminal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(if (isProcessing) TerminalAmber else TerminalGreen, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isProcessing) "RUNNING" else "IDLE", color = if (isProcessing) TerminalAmber else TerminalGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = onClearConsole,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(18.dp)
                    ) {
                        Text("CLEAR", color = TerminalRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Terminal Logs
                LazyColumn(
                    state = terminalListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (messages.isEmpty()) {
                        item { Text("--- RAW TERMINAL OUTPUT ---", color = TerminalGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                    } else {
                        items(messages, key = { it.id }) { message ->
                            // Use the old TerminalMessageItem that renders raw text
                            TerminalMessageItem(message)
                        }
                    }
                }
            }
        }

        // Keyboard Sidebar
        Column(
            modifier = Modifier
                .weight(0.10f)
                .fillMaxHeight()
                .glassPanel(shape = RoundedCornerShape(12.dp), alpha = 0.05f)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keyboardButtons = listOf(
                "COPY" to Icons.Default.ContentCopy,
                "PASTE" to Icons.Default.ContentPaste,
                "CTRL" to null,
                "ENTER" to Icons.Default.KeyboardReturn,
                "ESC" to null,
                "MIC" to Icons.Default.Mic
            )
            keyboardButtons.forEach { (label, icon) ->
                Button(
                    onClick = {
                        when (label) {
                            "MIC" -> {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your vibe code prompt...")
                                }
                                try { speechLauncher.launch(intent) } catch (e: Exception) { }
                            }
                            "COPY" -> {
                                currentFile?.let { f -> 
                                    clipboardManager.setText(AnnotatedString(fileContents[f.path] ?: ""))
                                }
                            }
                            "PASTE" -> { 
                                val t = clipboardManager.getText()?.text
                                if (t != null) {
                                    currentFile?.let { f ->
                                        val old = fileContents[f.path] ?: ""
                                        fileContents = fileContents + (f.path to old + t)
                                    }
                                }
                            }
                            "ENTER" -> {
                                currentFile?.let { f ->
                                    val old = fileContents[f.path] ?: ""
                                    fileContents = fileContents + (f.path to old + "\n")
                                }
                            }
                            "CTRL" -> { 
                                // Send SIGINT equivalent by killing running opencode/bash processes
                                if (config.executionMode == ExecutionMode.TERMUX_SERVICE) {
                                    coroutineScope.launch {
                                        TermuxRunner.executeCommand(context, "pkill -f opencode", config.workspacePath)
                                    }
                                }
                            }
                            "ESC" -> { 
                                // Escape the editor view by clearing the current file selection
                                currentFile = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha=0.05f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp).border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(8.dp))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (icon != null) {
                            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalMessageItem(message: ChatMessage) {
    val prefixColor = when (message.type) {
        MessageType.USER -> TerminalCyan
        MessageType.AGENT_THOUGHT -> TerminalAmber
        MessageType.AGENT_RESPONSE -> TerminalGreen
        MessageType.TOOL_CALL -> TerminalCyan
        MessageType.TOOL_OUTPUT -> TerminalGreenDim
        MessageType.SYSTEM_INFO -> TerminalGray
        MessageType.SYSTEM_ERROR -> TerminalRed
    }

    val prefix = when (message.type) {
        MessageType.USER -> "user@android:~\$ "
        MessageType.AGENT_THOUGHT -> "[AGENT_THINKING] "
        MessageType.AGENT_RESPONSE -> "[AGENT_RESPONSE]\n"
        MessageType.TOOL_CALL -> "[EXEC_TOOL] "
        MessageType.TOOL_OUTPUT -> "[STDOUT]\n"
        MessageType.SYSTEM_INFO -> "[SYS_INFO] "
        MessageType.SYSTEM_ERROR -> "[SYS_ERROR] "
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row {
            Text(
                text = prefix,
                color = prefixColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            if (message.type != MessageType.AGENT_RESPONSE && message.type != MessageType.TOOL_OUTPUT) {
                // Truncate massive strings to prevent Compose Text layout OOM / height crashes
                val safeText = if (message.text.length > 10000) message.text.take(10000) + "\n...[OUTPUT TRUNCATED]" else message.text
                Text(
                    text = safeText,
                    color = TerminalWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        if (message.type == MessageType.AGENT_RESPONSE || message.type == MessageType.TOOL_OUTPUT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp)
                    .glassPanel(shape = RoundedCornerShape(8.dp), alpha = 0.05f)
                    .padding(8.dp)
            ) {
                // Truncate massive strings to prevent Compose Text layout OOM / height crashes
                val safeText = if (message.text.length > 15000) message.text.take(15000) + "\n...[OUTPUT TRUNCATED]" else message.text
                Text(
                    text = safeText,
                    color = if (message.type == MessageType.TOOL_OUTPUT) TerminalGreenDim else TerminalWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
