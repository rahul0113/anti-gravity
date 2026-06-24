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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
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
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
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

    Column(modifier = modifier.fillMaxSize().background(Color.Transparent)) {
        
        // ChatGPT-like Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp).glassButton()) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
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

        // ChatGPT-like Modern Input Box - Liquid Glass
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .glassPanel(shape = RoundedCornerShape(24.dp), alpha = 0.05f)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { if (!isProcessing) inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 15.sp,
                    color = TerminalWhite,
                    fontFamily = FontFamily.SansSerif
                ),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text("Ask Anti-Gravity...", color = TerminalGray.copy(alpha = 0.7f), fontSize = 15.sp)
                    }
                    innerTextField()
                },
                maxLines = 5
            )

            if (inputText.trim().isEmpty()) {
                // Empty state — no buttons
            } else {
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
                        .background(Color.White.copy(alpha=0.2f), RoundedCornerShape(18.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
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
                color = Color.White.copy(alpha=0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.glassPanel(shape = RoundedCornerShape(8.dp), alpha = 0.1f).padding(horizontal = 8.dp, vertical = 4.dp)
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
                    .glassPanel(shape = RoundedCornerShape(16.dp), alpha = 0.2f),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "AI", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .glassPanel(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, 
                        topEnd = 16.dp, 
                        bottomStart = if (isUser) 16.dp else 0.dp, 
                        bottomEnd = if (isUser) 0.dp else 16.dp
                    ),
                    alpha = if (isUser) 0.15f else 0.05f
                )
                .padding(12.dp)
        ) {
            val safeText = if (message.text.length > 15000) message.text.take(15000) + "\n...[OUTPUT TRUNCATED]" else message.text
            Text(
                text = safeText,
                color = Color.White,
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
                    .glassPanel(shape = RoundedCornerShape(16.dp), alpha = 0.15f),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "User", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
