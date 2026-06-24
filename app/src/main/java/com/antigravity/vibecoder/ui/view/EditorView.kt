package com.antigravity.vibecoder.ui.view

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.data.SshConnection
import com.antigravity.vibecoder.data.TermuxRunner
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.MessageType
import com.antigravity.vibecoder.model.ConnectionConfig
import com.antigravity.vibecoder.model.ExecutionMode
import com.antigravity.vibecoder.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun String.shellSingleQuote(): String = "'" + this.replace("'", "'\\''") + "'"

// ─── Data class for file info ───
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val permissions: String = ""
) {
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "")
    val sizeFormatted: String get() = when {
        isDirectory -> "DIR"
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        else -> "${size / (1024 * 1024)}MB"
    }
}

@Composable
fun EditorView(
    messages: List<ChatMessage>,
    isProcessing: Boolean,
    config: ConnectionConfig,
    onSendPrompt: (String) -> Unit,
    onClearConsole: () -> Unit,
    onConfigChange: (ConnectionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ─── File Manager State ───
    var currentPath by rememberSaveable { mutableStateOf(config.workspacePath) }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var selectedFile by rememberSaveable { mutableStateOf<FileItem?>(null) }
    var openFiles by rememberSaveable { mutableStateOf<List<FileItem>>(emptyList()) }
    var currentEditFile by rememberSaveable { mutableStateOf<FileItem?>(null) }
    var fileContents by remember { mutableStateOf<Map<String, TextFieldValue>>(emptyMap()) }
    var isFileExplorerExpanded by rememberSaveable { mutableStateOf(true) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }

    // ─── Terminal State ───
    var terminalLogs by remember { mutableStateOf<List<TerminalEntry>>(emptyList()) }
    var terminalInput by remember { mutableStateOf("") }
    val terminalListState = rememberLazyListState()

    // ─── Clipboard ───
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // ─── Load files ───
    fun loadFiles(path: String) {
        isLoadingFiles = true
        coroutineScope.launch {
            val files = when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    val qPath = path.shellSingleQuote()
                    val cmd = "ls -la $qPath 2>/dev/null"
                    val res = TermuxRunner.executeCommand(context, cmd, path)
                    if (res.error == null) parseTermuxLaOutput(res.stdout, path) else emptyList()
                }
                ExecutionMode.SSH -> {
                    val result = SshConnection.listDirectory(config, path)
                    result.map { FileItem(it.name, it.path, it.isDirectory, it.size, it.lastModified) }
                }
                ExecutionMode.OPENCLAUDE, ExecutionMode.SANDBOX -> {
                    withContext(Dispatchers.IO) {
                        try {
                            val dir = File(path)
                            dir.listFiles()?.map { f ->
                                FileItem(
                                    name = f.name,
                                    path = f.absolutePath,
                                    isDirectory = f.isDirectory,
                                    size = if (f.isFile) f.length() else 0,
                                    lastModified = f.lastModified()
                                )
                            }?.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
                                ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            }
            fileList = files
            isLoadingFiles = false
        }
    }

    fun parseTermuxLaOutput(stdout: String, basePath: String): List<FileItem> {
        return stdout.lines().mapNotNull { line ->
            // Parse `ls -la` output: permissions links owner size date name
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 8) {
                val perms = parts[0]
                val size = parts[4].toLongOrNull() ?: 0L
                val name = parts.drop(7).joinToString(" ")
                if (name == "." || name == "..") return@mapNotNull null
                val fullPath = if (basePath.endsWith("/")) "$basePath$name" else "$basePath/$name"
                FileItem(
                    name = name,
                    path = fullPath,
                    isDirectory = perms.startsWith("d"),
                    size = size,
                    permissions = perms
                )
            } else null
        }.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun navigateTo(path: String) {
        currentPath = path
        selectedFile = null
        currentEditFile = null
        loadFiles(path)
    }

    fun navigateUp() {
        val parent = File(currentPath).parent ?: return
        navigateTo(parent)
    }

    fun deleteFile(file: FileItem) {
        coroutineScope.launch {
            val success = when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    val cmd = if (file.isDirectory) "rm -rf ${file.path.shellSingleQuote()}" else "rm -f ${file.path.shellSingleQuote()}"
                    val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                    res.error == null && res.exitCode == 0
                }
                ExecutionMode.OPENCLAUDE, ExecutionMode.SANDBOX -> {
                    withContext(Dispatchers.IO) {
                        try {
                            val f = File(file.path)
                            if (f.isDirectory) f.deleteRecursively() else f.delete()
                        } catch (e: Exception) { false }
                    }
                }
                ExecutionMode.SSH -> {
                    val cmd = if (file.isDirectory) "rm -rf ${file.path.shellSingleQuote()}" else "rm -f ${file.path.shellSingleQuote()}"
                    SshConnection.executeCommand(config, cmd)
                    true
                }
            }
            if (success) {
                openFiles = openFiles.filter { it.path != file.path }
                if (currentEditFile?.path == file.path) currentEditFile = openFiles.firstOrNull()
                loadFiles(currentPath)
            }
        }
    }

    fun createFile(name: String) {
        coroutineScope.launch {
            val fullPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
            when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    TermuxRunner.executeCommand(context, "touch ${fullPath.shellSingleQuote()}", config.workspacePath)
                }
                ExecutionMode.OPENCLAUDE, ExecutionMode.SANDBOX -> {
                    withContext(Dispatchers.IO) { File(fullPath).createNewFile() }
                }
                ExecutionMode.SSH -> {
                    SshConnection.executeCommand(config, "touch ${fullPath.shellSingleQuote()}")
                }
            }
            loadFiles(currentPath)
            showNewFileDialog = false
        }
    }

    fun createFolder(name: String) {
        coroutineScope.launch {
            val fullPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
            when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    TermuxRunner.executeCommand(context, "mkdir -p ${fullPath.shellSingleQuote()}", config.workspacePath)
                }
                ExecutionMode.OPENCLAUDE, ExecutionMode.SANDBOX -> {
                    withContext(Dispatchers.IO) { File(fullPath).mkdirs() }
                }
                ExecutionMode.SSH -> {
                    SshConnection.executeCommand(config, "mkdir -p ${fullPath.shellSingleQuote()}")
                }
            }
            loadFiles(currentPath)
            showNewFolderDialog = false
        }
    }

    fun renameFile(file: FileItem, newName: String) {
        coroutineScope.launch {
            val parent = File(file.path).parent ?: return@launch
            val newPath = "$parent/$newName"
            when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    TermuxRunner.executeCommand(context, "mv ${file.path.shellSingleQuote()} ${newPath.shellSingleQuote()}", config.workspacePath)
                }
                ExecutionMode.OPENCLAUDE, ExecutionMode.SANDBOX -> {
                    withContext(Dispatchers.IO) { File(file.path).renameTo(File(newPath)) }
                }
                ExecutionMode.SSH -> {
                    SshConnection.executeCommand(config, "mv ${file.path.shellSingleQuote()} ${newPath.shellSingleQuote()}")
                }
            }
            openFiles = openFiles.map { if (it.path == file.path) it.copy(name = newName, path = newPath) else it }
            if (currentEditFile?.path == file.path) currentEditFile = currentEditFile?.copy(name = newName, path = newPath)
            loadFiles(currentPath)
            showRenameDialog = false
        }
    }

    fun saveFile(file: FileItem, content: String) {
        coroutineScope.launch {
            val success = when (config.executionMode) {
                ExecutionMode.TERMUX_SERVICE -> {
                    if (content.length > 250_000) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "File too large for Termux IPC", Toast.LENGTH_LONG).show()
                        }
                        false
                    } else {
                        val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                        val cmd = "printf '%s' '$b64' | base64 -d > ${file.path.shellSingleQuote()}"
                        val res = TermuxRunner.executeCommand(context, cmd, config.workspacePath)
                        res.error == null && res.exitCode == 0
                    }
                }
                ExecutionMode.OPENCLAUDE, ExecutionMode.SANDBOX -> {
                    withContext(Dispatchers.IO) {
                        try { File(file.path).writeText(content); true } catch (e: Exception) { false }
                    }
                }
                ExecutionMode.SSH -> {
                    SshConnection.writeFile(config, file.path, content)
                }
            }
            if (success) loadFiles(currentPath)
        }
    }

    fun openFile(file: FileItem) {
        if (file.isDirectory) {
            navigateTo(file.path)
            return
        }
        if (!openFiles.any { it.path == file.path }) {
            openFiles = openFiles + file
        }
        currentEditFile = file
        if (!fileContents.containsKey(file.path)) {
            coroutineScope.launch {
                val content = when (config.executionMode) {
                    ExecutionMode.TERMUX_SERVICE -> {
                        if (file.size > 250_000) "// ERROR: File too large (${file.size / 1024} KB)"
                        else {
                            val res = TermuxRunner.executeCommand(context, "cat -- ${file.path.shellSingleQuote()}", config.workspacePath)
                            if (res.error == null) res.stdout else "Error: ${res.error}"
                        }
                    }
                    ExecutionMode.SSH -> SshConnection.readFile(config, file.path)
                    ExecutionMode.OPENCLAUDE, ExecutionMode.SANDBOX -> {
                        withContext(Dispatchers.IO) {
                            try { File(file.path).readText() } catch (e: Exception) { "Error: ${e.message}" }
                        }
                    }
                }
                fileContents = fileContents + (file.path to TextFieldValue(content, TextRange(0)))
            }
        }
    }

    // Initial load
    LaunchedEffect(currentPath) { loadFiles(currentPath) }

    // Auto-scroll terminal
    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    // ─── DIALOGS ───
    if (showNewFileDialog) {
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File", color = TerminalWhite) },
            text = {
                OutlinedTextField(
                    value = fileName, onValueChange = { fileName = it },
                    label = { Text("File name", color = TerminalGray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreen, focusedTextColor = TerminalWhite, unfocusedTextColor = TerminalWhite)
                )
            },
            confirmButton = { TextButton(onClick = { if (fileName.isNotBlank()) createFile(fileName) }) { Text("Create", color = TerminalGreen) } },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel", color = TerminalGray) } },
            containerColor = DarkBackground
        )
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder", color = TerminalWhite) },
            text = {
                OutlinedTextField(
                    value = folderName, onValueChange = { folderName = it },
                    label = { Text("Folder name", color = TerminalGray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreen, focusedTextColor = TerminalWhite, unfocusedTextColor = TerminalWhite)
                )
            },
            confirmButton = { TextButton(onClick = { if (folderName.isNotBlank()) createFolder(folderName) }) { Text("Create", color = TerminalGreen) } },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel", color = TerminalGray) } },
            containerColor = DarkBackground
        )
    }

    if (showRenameDialog && renameTarget != null) {
        var newName by remember { mutableStateOf(renameTarget?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", color = TerminalWhite) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("New name", color = TerminalGray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TerminalGreen, focusedTextColor = TerminalWhite, unfocusedTextColor = TerminalWhite)
                )
            },
            confirmButton = { TextButton(onClick = { if (newName.isNotBlank() && renameTarget != null) renameFile(renameTarget!!, newName) }) { Text("Rename", color = TerminalGreen) } },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = TerminalGray) } },
            containerColor = DarkBackground
        )
    }

    // ─── MAIN LAYOUT ───
    Row(modifier = modifier.fillMaxSize().background(Color.Transparent).padding(4.dp)) {

        // ═══ FILE EXPLORER SIDEBAR ═══
        if (isFileExplorerExpanded) {
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
                    .glassPanel(shape = RoundedCornerShape(12.dp), alpha = 0.06f)
                    .padding(8.dp)
            ) {
                // ─── Header ───
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FILES", color = TerminalGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // New File
                        IconButton(onClick = { showNewFileDialog = true }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = TerminalCyan, modifier = Modifier.size(14.dp))
                        }
                        // New Folder
                        IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = TerminalCyan, modifier = Modifier.size(14.dp))
                        }
                        // Refresh
                        IconButton(onClick = { loadFiles(currentPath) }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TerminalGreen, modifier = Modifier.size(14.dp))
                        }
                        // Sort toggle
                        IconButton(onClick = {
                            sortMode = when (sortMode) {
                                SortMode.NAME -> SortMode.SIZE
                                SortMode.SIZE -> SortMode.DATE
                                SortMode.DATE -> SortMode.NAME
                            }
                        }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = TerminalAmber, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // ─── Path Breadcrumb ───
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = { navigateUp() }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Up", tint = TerminalAmber, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = currentPath.removePrefix("/data/data/com.termux/files/home/").ifEmpty { "~" },
                        color = TerminalGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // ─── File List ───
                val sortedFiles = remember(fileList, sortMode, sortAscending) {
                    when (sortMode) {
                        SortMode.NAME -> fileList.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { if (sortAscending) it.name.lowercase() else it.name.lowercase().reversed() })
                        SortMode.SIZE -> fileList.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { if (sortAscending) it.size else -it.size })
                        SortMode.DATE -> fileList.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { if (sortAscending) it.lastModified else -it.lastModified })
                    }
                }

                if (isLoadingFiles) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TerminalGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(sortedFiles, key = { it.path }) { file ->
                            FileItemRow(
                                file = file,
                                isSelected = selectedFile?.path == file.path,
                                onClick = {
                                    selectedFile = file
                                    openFile(file)
                                },
                                onLongClick = {
                                    // Show rename/delete context
                                    selectedFile = file
                                }
                            )
                        }
                    }
                }

                // ─── File actions bar (when file selected) ───
                if (selectedFile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Rename
                        SmallButton("RENAME", TerminalAmber) {
                            renameTarget = selectedFile
                            showRenameDialog = true
                        }
                        // Delete
                        SmallButton("DELETE", TerminalRed) {
                            deleteFile(selectedFile!!)
                            selectedFile = null
                        }
                        // Copy path
                        SmallButton("PATH", TerminalCyan) {
                            val clip = ClipData.newPlainText("path", selectedFile!!.path)
                            clipboardManager.setPrimaryClip(clip)
                        }
                    }
                }
            }
        }

        // ═══ EDITOR + TERMINAL PANE ═══
        Column(
            modifier = Modifier
                .weight(if (isFileExplorerExpanded) 0.55f else 1f)
                .fillMaxHeight()
                .padding(end = 4.dp)
        ) {
            // ─── Toolbar ───
            Row(
                modifier = Modifier.fillMaxWidth().glassPanel(shape = RoundedCornerShape(10.dp), alpha = 0.08f).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isFileExplorerExpanded = !isFileExplorerExpanded }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (isFileExplorerExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Toggle Files", tint = TerminalGreen, modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("OPENCLAUDE", color = TerminalGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                if (currentEditFile != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(currentEditFile!!.name, color = TerminalWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(8.dp))
                        // Save button
                        Button(
                            onClick = {
                                val file = currentEditFile ?: return@Button
                                val content = fileContents[file.path]?.text ?: return@Button
                                saveFile(file, content)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TerminalGreenDim),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("SAVE", color = DarkBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ─── Tabs ───
            if (openFiles.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(openFiles) { file ->
                        val isActive = currentEditFile?.path == file.path
                        Row(
                            modifier = Modifier
                                .background(if (isActive) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable { currentEditFile = file }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (file.extension in listOf("kt", "java", "py", "js", "ts", "go", "rs")) Icons.Default.Code else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isActive) TerminalGreen else TerminalGray,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(file.name, color = if (isActive) TerminalWhite else TerminalGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Close, contentDescription = "Close",
                                tint = TerminalGray, modifier = Modifier.size(10.dp).clickable {
                                    openFiles = openFiles.filter { it.path != file.path }
                                    if (currentEditFile?.path == file.path) currentEditFile = openFiles.firstOrNull()
                                }
                            )
                        }
                    }
                }
            }

            // ─── Editor Area ───
            if (currentEditFile == null) {
                // Empty state
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TerminalGray.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("SELECT A FILE TO EDIT", color = TerminalGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("or create a new file", color = TerminalGray.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
            } else {
                // Code editor with line numbers
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .glassPanel(shape = RoundedCornerShape(8.dp), alpha = 0.04f)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val content = fileContents[currentEditFile?.path]?.text ?: ""
                        val lineCount = remember(content) { minOf(content.count { it == '\n' } + 1, 9999) }
                        val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

                        // Line numbers
                        Text(
                            text = lineNumbers,
                            color = TerminalGreenDim.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .widthIn(min = 32.dp)
                        )

                        // Code editor
                        BasicTextField(
                            value = fileContents[currentEditFile?.path] ?: TextFieldValue(""),
                            onValueChange = { newText ->
                                currentEditFile?.let { f ->
                                    fileContents = fileContents + (f.path to newText)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                color = TerminalWhite
                            ),
                            cursorBrush = SolidColor(TerminalGreen)
                        )
                    }
                }
            }

            // ─── Terminal Panel ───
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .glassPanel(shape = RoundedCornerShape(10.dp), alpha = 0.05f)
            ) {
                // Terminal header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(5.dp).background(if (isProcessing) TerminalAmber else TerminalGreen, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isProcessing) "OPENCLAUDE RUNNING" else "TERMINAL", color = if (isProcessing) TerminalAmber else TerminalGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Row {
                        SmallButton("CLEAR", TerminalRed) {
                            terminalLogs = emptyList()
                            onClearConsole()
                        }
                    }
                }

                // Terminal output
                LazyColumn(
                    state = terminalListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (terminalLogs.isEmpty() && messages.isEmpty()) {
                        item {
                            Text("--- OPENCLAUDE TERMINAL ---", color = TerminalGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    // OpenClaude agent messages
                    items(messages, key = { it.id }) { msg ->
                        TerminalMessageItem(msg)
                    }
                    // Local terminal logs
                    items(terminalLogs) { entry ->
                        Row {
                            Text(entry.prompt, color = TerminalGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(entry.command, color = TerminalWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (entry.output.isNotBlank()) {
                            Text(entry.output, color = TerminalGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // Terminal input
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$ ", color = TerminalGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = terminalInput,
                        onValueChange = { terminalInput = it },
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        textStyle = LocalTextStyle.current.copy(color = TerminalWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(TerminalGreen),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (terminalInput.isNotBlank()) {
                                val cmd = terminalInput.trim()
                                terminalInput = ""
                                coroutineScope.launch {
                                    val res = TermuxRunner.executeCommand(context, cmd, currentPath)
                                    val entry = TerminalEntry("$ ", cmd, res.stdout + if (res.stderr.isNotBlank()) "\n$REDACTED_stderr: ${res.stderr}" else "")
                                    terminalLogs = terminalLogs + entry
                                    loadFiles(currentPath)
                                }
                            }
                        })
                    }
                }
            }
        }

        // ═══ CUSTOM KEYBOARD SIDEBAR ═══
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .glassPanel(shape = RoundedCornerShape(12.dp), alpha = 0.07f)
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentFile = currentEditFile
            val currentVal = if (currentFile != null) fileContents[currentFile.path] else null

            // Helper to apply keyboard action
            fun applyKeyboardAction(action: (TextFieldValue) -> TextFieldValue) {
                if (currentFile != null && currentVal != null) {
                    val newVal = action(currentVal)
                    fileContents = fileContents + (currentFile.path to newVal)
                }
            }

            // ENTER
            KeyboardKey("ENTER", Icons.Default.KeyboardReturn) {
                applyKeyboardAction { tf ->
                    val pos = tf.selection.start
                    val text = tf.text
                    val newText = text.substring(0, pos) + "\n" + text.substring(pos)
                    TextFieldValue(newText, TextRange(pos + 1))
                }
            }

            // TAB
            KeyboardKey("TAB", Icons.Default.KeyboardTab) {
                applyKeyboardAction { tf ->
                    val pos = tf.selection.start
                    val text = tf.text
                    val newText = text.substring(0, pos) + "    " + text.substring(pos)
                    TextFieldValue(newText, TextRange(pos + 4))
                }
            }

            Spacer(Modifier.height(2.dp))

            // COPY
            KeyboardKey("COPY", Icons.Default.ContentCopy) {
                if (currentVal != null) {
                    val sel = currentVal.selection
                    val text = currentVal.text
                    if (sel.collapsed) {
                        // Copy whole line
                        val lineStart = text.lastIndexOf('\n', sel.start - 1).let { if (it < 0) 0 else it + 1 }
                        val lineEnd = text.indexOf('\n', sel.start).let { if (it < 0) text.length else it }
                        val clip = ClipData.newPlainText("code", text.substring(lineStart, lineEnd))
                        clipboardManager.setPrimaryClip(clip)
                    } else {
                        val clip = ClipData.newPlainText("code", text.substring(sel.start, sel.end))
                        clipboardManager.setPrimaryClip(clip)
                    }
                }
            }

            // PASTE
            KeyboardKey("PASTE", Icons.Default.ContentPaste) {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val pasteText = clip.getItemAt(0).text?.toString() ?: return@KeyboardKey
                    applyKeyboardAction { tf ->
                        val pos = tf.selection.start
                        val text = tf.text
                        val newText = text.substring(0, pos) + pasteText + text.substring(pos)
                        TextFieldValue(newText, TextRange(pos + pasteText.length))
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // CTRL
            KeyboardKey("CTRL", null) {
                if (config.executionMode == ExecutionMode.TERMUX_SERVICE) {
                    coroutineScope.launch {
                        TermuxRunner.executeCommand(context, "pkill -f openclaude", config.workspacePath)
                    }
                }
            }

            // ALT
            KeyboardKey("ALT", null) {
                // Alt modifier - insert alt code or do nothing (extensible)
            }

            // ESC
            KeyboardKey("ESC", Icons.Default.Close) {
                if (currentEditFile != null) {
                    openFiles = openFiles.filter { it.path != currentEditFile?.path }
                    currentEditFile = openFiles.firstOrNull()
                }
            }

            Spacer(Modifier.height(2.dp))

            // Arrow keys
            KeyboardKey("UP", Icons.Default.KeyboardArrowUp) {
                applyKeyboardAction { tf ->
                    val pos = tf.selection.start
                    val text = tf.text
                    val prevNewline = text.lastIndexOf('\n', maxOf(0, pos - 1))
                    tf.copy(selection = TextRange(maxOf(0, prevNewline)))
                }
            }
            KeyboardKey("DOWN", Icons.Default.KeyboardArrowDown) {
                applyKeyboardAction { tf ->
                    val pos = tf.selection.start
                    val text = tf.text
                    val nextNewline = text.indexOf('\n', pos)
                    val target = if (nextNewline == -1) text.length else nextNewline + 1
                    tf.copy(selection = TextRange(target))
                }
            }
            KeyboardKey("LEFT", Icons.Default.KeyboardArrowLeft) {
                applyKeyboardAction { tf ->
                    tf.copy(selection = TextRange(maxOf(0, tf.selection.start - 1)))
                }
            }
            KeyboardKey("RIGHT", Icons.Default.KeyboardArrowRight) {
                applyKeyboardAction { tf ->
                    tf.copy(selection = TextRange(minOf(tf.text.length, tf.selection.end + 1)))
                }
            }
        }
    }
}

// ─── Sub-components ───

@Composable
private fun FileItemRow(
    file: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                file.isDirectory -> Icons.Default.Folder
                file.extension in listOf("kt", "java") -> Icons.Default.Code
                file.extension in listOf("py", "js", "ts", "go", "rs") -> Icons.Default.Code
                file.extension in listOf("json", "xml", "yaml", "yml", "toml") -> Icons.Default.Settings
                file.extension in listOf("md", "txt", "log") -> Icons.Default.Description
                else -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            tint = when {
                file.isDirectory -> TerminalCyan
                file.extension in listOf("kt", "java") -> TerminalGreen
                file.extension in listOf("py", "js", "ts") -> TerminalAmber
                else -> TerminalGray
            },
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = if (isSelected) TerminalGreen else TerminalWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        Text(
            text = file.sizeFormatted,
            color = TerminalGray.copy(alpha = 0.6f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun KeyboardKey(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            } else {
                Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        modifier = Modifier.height(20.dp)
    ) {
        Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

// ─── Data classes ───
data class TerminalEntry(val prompt: String, val command: String, val output: String = "")
enum class SortMode { NAME, SIZE, DATE }

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
        MessageType.USER -> "$ "
        MessageType.AGENT_THOUGHT -> "[THINK] "
        MessageType.AGENT_RESPONSE -> ""
        MessageType.TOOL_CALL -> "[TOOL] "
        MessageType.TOOL_OUTPUT -> ""
        MessageType.SYSTEM_INFO -> "[INFO] "
        MessageType.SYSTEM_ERROR -> "[ERR] "
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        if (prefix.isNotEmpty()) {
            Text(prefix, color = prefixColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (message.text.isNotBlank()) {
            val safeText = if (message.text.length > 15000) message.text.take(15000) + "\n...[TRUNCATED]" else message.text
            Text(
                text = safeText,
                color = when (message.type) {
                    MessageType.AGENT_RESPONSE -> TerminalWhite
                    MessageType.TOOL_OUTPUT -> TerminalGreenDim
                    MessageType.SYSTEM_ERROR -> TerminalRed
                    else -> TerminalWhite.copy(alpha = 0.9f)
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}

// Redacted constant for stderr label
private const val REDACTED_stderr = "stderr"
