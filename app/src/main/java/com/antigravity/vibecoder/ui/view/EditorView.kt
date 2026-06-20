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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// SEC-2 FIX: Proper single-quote escaping for shell commands to prevent path injection
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
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    var currentFileName by remember { mutableStateOf<String>("No File Selected") }
    var fileContent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isExplorerExpanded by remember { mutableStateOf(true) }

    val localWorkspace = File(context.filesDir, "workspace").apply { mkdirs() }
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
        val list = mutableListOf<WorkspaceFile>()
        stdout.lines().forEach { line ->
            val parts = line.split("\t")
            if (parts.size >= 3) {
                val name = parts[0].trim()
                if (name.isEmpty() || name == "*") return@forEach
                val size = parts[1].trim().toLongOrNull() ?: 0L
                val type = parts[2].trim()
                val isDir = type == "d"
                val fullPath = if (basePath.endsWith("/")) "$basePath$name" else "$basePath/$name"
                list.add(WorkspaceFile(name, fullPath, isDir, size))
            }
        }
        return list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun reloadFiles() {
        coroutineScope.launch {
            fileList = when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    // SEC-2 FIX: Use single-quoted path in shell command
                    val path = config.workspacePath.shellSingleQuote()
                    val cmd = "cd $path && for f in *; do [ -d \"\$f\" ] && echo -e \"\$f\\t0\\td\" || echo -e \"\$f\\t\$(stat -c%s \"\$f\")\\tf\"; done"
                    val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                    if (res.error == null) parseTermuxLs(res.stdout, config.workspacePath) else emptyList()
                }
                ExecutionMode.SSH -> SshConnection.listDirectory(config, config.workspacePath)
                ExecutionMode.SANDBOX -> {
                    localWorkspace.listFiles()
                        ?.map { WorkspaceFile(it.name, it.absolutePath, it.isDirectory, it.length()) }
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        ?: emptyList()
                }
            }
        }
    }

    // BUG-1 FIX: Use Unit key so this only fires once on composition, not on every config recompose
    LaunchedEffect(Unit) {
        reloadFiles()
    }

    // BUG-2 FIX: Shared scroll state syncs line numbers with the editor text field
    val editorScrollState = rememberScrollState()

    Row(modifier = modifier.fillMaxSize().background(com.antigravity.vibecoder.ui.theme.DarkBackground)) {
        // Collapsible File Drawer Sidebar
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
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Files", tint = TerminalGreen, modifier = Modifier.size(16.dp))
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    items(fileList) { wFile ->
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
                                                    // SEC-2 FIX: Single-quote the file path to prevent injection
                                                    val quotedPath = wFile.path.shellSingleQuote()
                                                    val res = TermuxRunner.executeCommand(context, "cat -- $quotedPath", config.workspacePath)
                                                    if (res.error == null) res.stdout else "Error reading file: ${res.error}"
                                                }
                                                ExecutionMode.SSH -> SshConnection.readFile(config, wFile.path)
                                                ExecutionMode.SANDBOX -> try {
                                                    File(wFile.path).readText()
                                                } catch (e: Exception) {
                                                    "Failed to read file: ${e.message}"
                                                }
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

        // Code Editor Pane
        Column(
            modifier = Modifier
                .weight(if (isExplorerExpanded) 0.55f else 0.90f)
                .fillMaxHeight()
        ) {
            // Editor Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isExplorerExpanded = !isExplorerExpanded }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (isExplorerExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = TerminalGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "> $currentFileName",
                        color = TerminalCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (currentFilePath != null) {
                    Button(
                        onClick = {
                            val path = currentFilePath ?: return@Button
                            isSaving = true
                            coroutineScope.launch {
                                val success = when (config.executionMode) {
                                    ExecutionMode.TERMUX_SERVICE -> {
                                        // SEC-2 FIX: Use base64 encoding to safely write file content
                                        val b64 = android.util.Base64.encodeToString(
                                            fileContent.toByteArray(Charsets.UTF_8),
                                            android.util.Base64.NO_WRAP
                                        )
                                        val quotedPath = path.shellSingleQuote()
                                        val cmd = "mkdir -p \$(dirname $quotedPath) && printf '%s' '$b64' | base64 -d > $quotedPath"
                                        val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                                        res.error == null && res.exitCode == 0
                                    }
                                    ExecutionMode.SSH -> SshConnection.writeFile(config, path, fileContent)
                                    ExecutionMode.SANDBOX -> try {
                                        File(path).writeText(fileContent); true
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
                        Text(
                            text = if (isSaving) "SAVING..." else "SAVE",
                            color = DarkBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Editor Work Area
            if (currentFilePath == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("SELECT A FILE TO VIBE EDIT", color = TerminalGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                // CRASH-5 FIX: Removed conflicting .fillMaxSize() from the Row that has .weight(1f)
                // ARCH-3 / BUG-2 FIX: Use shared editorScrollState so line numbers scroll in sync
                Row(
                    modifier = Modifier
                        .weight(1f)       // Takes remaining height in the Column
                        .fillMaxWidth()
                ) {
                    val lineCount = fileContent.lines().size
                    val lineNumbers = (1..lineCount).joinToString("\n") { it.toString() }

                    // BUG-2 FIX: Line numbers share editorScrollState — they scroll together
                    Text(
                        text = lineNumbers,
                        color = TerminalGreenDim.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(DarkSurface)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .verticalScroll(editorScrollState)
                    )

                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .verticalScroll(editorScrollState),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TerminalWhite
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }

        // Custom Keyboard Sidebar
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
                                try { speechLauncher.launch(intent) } catch (e: Exception) { /* no speech app */ }
                            }
                            "COPY" -> clipboardManager.setText(AnnotatedString(fileContent))
                            "PASTE" -> { val t = clipboardManager.getText()?.text; if (t != null) fileContent += t }
                            "ENTER" -> fileContent += "\n"
                            "CTRL" -> { /* Future: send ^C signal via Termux */ }
                            "ESC" -> { /* Future: send ESC sequence via Termux */ }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalGreenDim.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, TerminalGreenDim.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (icon != null) {
                            Icon(imageVector = icon, contentDescription = label, tint = TerminalGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(text = label, color = TerminalGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }
        }
    }
}
