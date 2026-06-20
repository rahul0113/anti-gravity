package com.antigravity.vibecoder.ui.view

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
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

@Composable
fun EditorView(
    config: ConnectionConfig,
    onSendPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var fileList by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    var currentFileName by remember { mutableStateOf<String>("No File Selected") }
    var fileContent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isExplorerExpanded by remember { mutableStateOf(true) }

    val localWorkspace = File(LocalContext.current.filesDir, "workspace").apply { mkdirs() }
    val clipboardManager = LocalClipboardManager.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
            if (!spokenText.isNullOrEmpty()) {
                onSendPrompt(spokenText)
            }
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

    // Helper function to load workspace file list
    fun reloadFiles() {
        coroutineScope.launch {
            when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    val cmd = "cd ${config.workspacePath} && for f in *; do [ -d \"\$f\" ] && echo -e \"\$f\\t0\\td\" || echo -e \"\$f\\t\$(stat -c%s \"\$f\")\\tf\"; done"
                    val res = TermuxRunner.executeCommand(getLocalContextHelper(), cmd, config.workspacePath)
                    fileList = if (res.error == null) parseTermuxLs(res.stdout, config.workspacePath) else emptyList()
                }
                ExecutionMode.SSH -> {
                    fileList = SshConnection.listDirectory(config, config.workspacePath)
                }
                ExecutionMode.SANDBOX -> {
                    val list = mutableListOf<WorkspaceFile>()
                    localWorkspace.listFiles()?.forEach { file ->
                        list.add(WorkspaceFile(file.name, file.absolutePath, file.isDirectory, file.length()))
                    }
                    fileList = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                }
            }
        }
    }

    // Load file list on start
    LaunchedEffect(config) {
        reloadFiles()
    }

    Row(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        // Collapsible File Drawer Sidebar
        if (isExplorerExpanded) {
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .border(end = 1.dp, color = DarkBorder)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FILES", color = TerminalGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { reloadFiles() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Files", tint = TerminalGreen, modifier = Modifier.size(16.dp))
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
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
                                                    val res = TermuxRunner.executeCommand(getLocalContextHelper(), "cat \"${wFile.path}\"", config.workspacePath)
                                                     if (res.error == null) res.stdout else "Error reading file: ${res.error}"
                                                }
                                                ExecutionMode.SSH -> {
                                                    SshConnection.readFile(config, wFile.path)
                                                }
                                                ExecutionMode.SANDBOX -> {
                                                    try {
                                                        File(wFile.path).readText()
                                                    } catch (e: Exception) {
                                                        "Failed to read file: ${e.message}"
                                                    }
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
                            Spacer(modifier = Modifier.width(6.dp))
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
                    .border(bottom = 1.dp, color = DarkBorder)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isExplorerExpanded = !isExplorerExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExplorerExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar",
                            tint = TerminalGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                                        val b64 = android.util.Base64.encodeToString(fileContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                                        val cmd = "mkdir -p \$(dirname \"$path\") && echo \"$b64\" | base64 -d > \"$path\""
                                        val res = TermuxRunner.executeCommand(getLocalContextHelper(), cmd, config.workspacePath)
                                        res.error == null && res.exitCode == 0
                                    }
                                    ExecutionMode.SSH -> {
                                        SshConnection.writeFile(config, path, fileContent)
                                    }
                                    ExecutionMode.SANDBOX -> {
                                        try {
                                            File(path).writeText(fileContent)
                                            true
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                }
                                isSaving = false
                                if (success) {
                                    reloadFiles()
                                }
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SELECT A FILE TO VIBE EDIT",
                        color = TerminalGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Line numbers column
                    val lineCount = fileContent.lines().size
                    val lineNumbers = (1..lineCount).joinToString("\n") { it.toString() }
                    
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
                            .verticalScroll(rememberScrollState())
                    )

                    // Text Editor Field
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TerminalWhite
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
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
                .border(start = 1.dp, color = DarkBorder)
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
                                try {
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {}
                            }
                            "COPY" -> {
                                clipboardManager.setText(AnnotatedString(fileContent))
                            }
                            "PASTE" -> {
                                val clipText = clipboardManager.getText()?.text
                                if (clipText != null) {
                                    fileContent += clipText
                                }
                            }
                            "ENTER" -> {
                                fileContent += "\n"
                            }
                            "CTRL", "ESC" -> {
                                // To be mapped to terminal signals in future iterations
                            }
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = TerminalGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = label,
                            color = TerminalGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Composition helper to fetch context within Compose
@Composable
fun getLocalContextHelper(): Context = LocalContext.current
