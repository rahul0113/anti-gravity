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
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.model.WorkspaceFile
import com.antigravity.vibecoder.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

private fun String.shellSingleQuote(): String = "'" + this.replace("'", "'\\''") + "'"

@Composable
fun EditorView(
    config: ConnectionConfig,
    onSendPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fileList by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }

    // B-6 FIX: rememberSaveable preserves editor state across recompositions and config changes
    var currentFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var currentFileName by rememberSaveable { mutableStateOf("No File Selected") }
    var fileContent by rememberSaveable { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var isExplorerExpanded by remember { mutableStateOf(true) }

    val localWorkspace = remember { File(context.filesDir, "workspace") }
    val clipboardManager = LocalClipboardManager.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
            if (!spokenText.isNullOrEmpty()) onSendPrompt(spokenText)
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

    fun reloadFiles() {
        coroutineScope.launch {
            fileList = when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    val path = config.workspacePath.shellSingleQuote()
                    val cmd = "cd $path && for f in *; do [ -d \"\$f\" ] && echo -e \"\$f\\t0\\td\" || echo -e \"\$f\\t\$(stat -c%s \"\$f\")\\tf\"; done"
                    val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                    if (res.error == null) parseTermuxLs(res.stdout, config.workspacePath) else emptyList()
                }
                ExecutionMode.SSH -> SshConnection.listDirectory(config, config.workspacePath)
                ExecutionMode.SANDBOX -> {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        localWorkspace.mkdirs()
                        localWorkspace.listFiles()
                            ?.map { WorkspaceFile(it.name, it.absolutePath, it.isDirectory, it.length()) }
                            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                            ?: emptyList()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { reloadFiles() }

    // C-1 FIX: Single shared scrollState on the outer Box — no nested scroll conflict
    val editorScrollState = rememberScrollState()

    Row(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        // File Explorer Sidebar
        if (isExplorerExpanded) {
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .border(1.dp, DarkBorder)
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
                                        currentFilePath = wFile.path
                                        currentFileName = wFile.name
                                        coroutineScope.launch {
                                            fileContent = when (config.executionMode) {
                                                ExecutionMode.TERMUX_SERVICE -> {
                                                    // V-1 FIX: Termux intents crash if payload is > ~1MB. Restrict to 250KB.
                                                    if (wFile.size > 250_000) {
                                                        "// ERROR: File is too large (${wFile.size / 1024} KB).\n// Termux IPC intent transactions are limited to ~250KB.\n// Please switch to SSH mode to safely edit large files."
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
                                color = if (currentFilePath == wFile.path) TerminalGreen else TerminalWhite,
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
        ) {
            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().background(DarkSurface).border(1.dp, DarkBorder).padding(8.dp),
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
                    Spacer(Modifier.width(8.dp))
                    Text("> $currentFileName", color = TerminalCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                if (currentFilePath != null) {
                    Button(
                        onClick = {
                            val path = currentFilePath ?: return@Button
                            isSaving = true
                            coroutineScope.launch {
                                val success = when (config.executionMode) {
                                    ExecutionMode.TERMUX_SERVICE -> {
                                        // V-1 FIX: Prevent intent transaction crash on save
                                        if (fileContent.length > 250_000) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, "File too large (>250KB) for Termux IPC. Use SSH mode.", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                            false
                                        } else {
                                            val b64 = android.util.Base64.encodeToString(
                                                fileContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
                                            )
                                            val qPath = path.shellSingleQuote()
                                            val cmd = "mkdir -p \$(dirname $qPath) && printf '%s' '$b64' | base64 -d > $qPath"
                                            val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                                            res.error == null && res.exitCode == 0
                                        }
                                    }
                                    ExecutionMode.SSH -> SshConnection.writeFile(config, path, fileContent)
                                    ExecutionMode.SANDBOX -> try { 
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            File(path).writeText(fileContent)
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

            if (currentFilePath == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("SELECT A FILE TO VIBE EDIT", color = TerminalGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                // C-1 FIX: Single outer Box with verticalScroll — no nested scroll conflict.
                // Both line numbers and BasicTextField live inside this scrollable container.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(editorScrollState)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Line numbers — P-1 FIX: Avoid string allocation (fileContent.lines().size creates thousands of objects)
                        // Using count { it == '\n' } is O(N) but zero allocation.
                        val lineCount = remember(fileContent) { fileContent.count { it == '\n' } + 1 }
                        val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

                        Text(
                            text = lineNumbers,
                            color = TerminalGreenDim.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .background(DarkSurface)
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )

                        // C-1 FIX: BasicTextField has no internal scroll — the parent Box scrolls it
                        BasicTextField(
                            value = fileContent,
                            onValueChange = { fileContent = it },
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
        }

        // Keyboard Sidebar
        Column(
            modifier = Modifier
                .weight(0.10f)
                .fillMaxHeight()
                .background(DarkSurface)
                .border(1.dp, DarkBorder)
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
                            "COPY" -> clipboardManager.setText(AnnotatedString(fileContent))
                            "PASTE" -> { val t = clipboardManager.getText()?.text; if (t != null) fileContent += t }
                            "ENTER" -> fileContent += "\n"
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
                                currentFilePath = null
                                currentFileName = "No File Selected"
                                fileContent = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalGreenDim.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, TerminalGreenDim.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (icon != null) {
                            Icon(icon, contentDescription = label, tint = TerminalGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(label, color = TerminalGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }
        }
    }
}
