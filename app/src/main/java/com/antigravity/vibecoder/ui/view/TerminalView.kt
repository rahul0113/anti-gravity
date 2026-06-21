package com.antigravity.vibecoder.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.MessageType
import com.antigravity.vibecoder.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TerminalView(
    messages: List<ChatMessage>,
    isProcessing: Boolean,
    onSendPrompt: (String) -> Unit,
    onClearConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // B-3 FIX: Use messages.lastOrNull() as key — correctly detects clear+add (same size) events
    // O-8 FIX: Also only auto-scroll when user is near the bottom
    LaunchedEffect(messages.lastOrNull()) {
        if (messages.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            // Only auto-scroll if user is within 3 messages of the bottom
            val isNearBottom = lastVisibleIndex >= totalItems - 3 || totalItems <= 3
            if (isNearBottom) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Status & Console Controls Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .border(1.dp, color = DarkBorder)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isProcessing) TerminalAmber else TerminalGreen,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isProcessing) "AGENT_BUSY (THINKING/RUNNING)" else "AGENT_ONLINE (IDLE)",
                    color = if (isProcessing) TerminalAmber else TerminalGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // COPY LOG button — copies all messages to clipboard
                Button(
                    onClick = {
                        val logText = buildString {
                            for (msg in messages) {
                                appendLine("[${msg.type.name}] ${msg.sender}: ${msg.text}")
                                appendLine("---")
                            }
                        }
                        clipboardManager.setText(AnnotatedString(logText))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .border(1.dp, TerminalCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .height(24.dp)
                ) {
                    Text("COPY LOG", color = TerminalCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClearConsole,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .border(1.dp, TerminalRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .height(24.dp)
                ) {
                    Text("RESET_LOGS", color = TerminalRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Output Stream Log Panel (Chat)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = "AI", tint = TerminalGreen, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "How can I help you code today?",
                            color = TerminalWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            } else {
                // Filter messages: Only show user, agent responses, and maybe tool calls as status
                val chatMessages = messages.filter { 
                    it.type == MessageType.USER || 
                    it.type == MessageType.AGENT_RESPONSE || 
                    it.type == MessageType.TOOL_CALL 
                }
                
                items(chatMessages, key = { it.id }) { message ->
                    ChatMessageItem(message)
                }
                
                if (isProcessing) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TerminalGreen, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Agent is thinking...", color = TerminalGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Modern Chat Input Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(DarkSurface, shape = RoundedCornerShape(24.dp))
                .border(1.dp, color = DarkBorder, shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { if (!isProcessing) inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 15.sp,
                    color = TerminalWhite,
                    fontFamily = FontFamily.SansSerif
                ),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text("Message the AI Agent...", color = TerminalGray.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    innerTextField()
                },
                maxLines = 5
            )

            IconButton(
                onClick = {
                    if (inputText.trim().isNotEmpty()) {
                        onSendPrompt(inputText)
                        inputText = ""
                    }
                },
                enabled = !isProcessing && inputText.trim().isNotEmpty(),
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (!isProcessing && inputText.trim().isNotEmpty()) TerminalGreen else DarkBorder, 
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = DarkBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.type == MessageType.USER
    val isToolCall = message.type == MessageType.TOOL_CALL
    
    if (isToolCall) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚡ Running: ${message.text.take(50).replace("\n", " ")}...",
                color = TerminalGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.background(DarkSurface, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(TerminalGreen.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .border(1.dp, TerminalGreen, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "AI", tint = TerminalGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .background(
                    if (isUser) DarkSurface else Color.Transparent,
                    RoundedCornerShape(
                        topStart = 16.dp, 
                        topEnd = 16.dp, 
                        bottomStart = if (isUser) 16.dp else 0.dp, 
                        bottomEnd = if (isUser) 0.dp else 16.dp
                    )
                )
                .padding(if (isUser) 12.dp else 4.dp)
        ) {
            Text(
                text = message.text,
                color = TerminalWhite,
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 22.sp
            )
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "User", tint = TerminalGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}
