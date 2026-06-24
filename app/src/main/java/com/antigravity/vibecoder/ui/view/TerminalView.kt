package com.antigravity.vibecoder.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.vibecoder.model.ChatMessage
import com.antigravity.vibecoder.model.MessageType
import com.antigravity.vibecoder.ui.theme.*

@Composable
fun TerminalView(
    messages: List<ChatMessage>,
    isProcessing: Boolean,
    onSendPrompt: (String) -> Unit,
    onClearConsole: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll
    LaunchedEffect(messages.size) {
        if (messages.size > 2) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TerminalWhite)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Chat",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalWhite
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClearConsole) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = TerminalRed.copy(alpha = 0.7f))
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            state = listState
        ) {
            items(messages.filter {
                it.type == MessageType.USER || it.type == MessageType.AGENT_RESPONSE || it.type == MessageType.TOOL_CALL
            }) { message ->
                when (message.type) {
                    MessageType.TOOL_CALL -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = TerminalAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Running: ${message.text.take(50)}...",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = TerminalAmber,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    MessageType.USER -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                color = TerminalGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = message.text.take(15000),
                                        color = TerminalWhite,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.Person, null,
                                        tint = TerminalGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    MessageType.AGENT_RESPONSE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.SmartToy, null,
                                        tint = TerminalCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = message.text.take(15000),
                                        color = TerminalWhite,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Loading indicator
            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TerminalCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Agent is thinking...", color = TerminalCyan, fontSize = 13.sp)
                    }
                }
            }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = LocalTextStyle.current.copy(color = TerminalWhite, fontSize = 14.sp),
                cursorBrush = SolidColor(TerminalGreen),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text("Ask AntiGravity...", color = TerminalGray, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                }
            )

            if (inputText.isNotBlank()) {
                IconButton(
                    onClick = {
                        onSendPrompt(inputText)
                        inputText = ""
                    }
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = TerminalGreen
                    )
                }
            }
        }
    }
}
