package com.antigravity.vibecoder.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
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

        // Output Stream Log Panel
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "--- ANTI-GRAVITY VIBE CODER CLI v1.0.0 ---\nReady for autonomous coding actions.\nProvide an instruction below to start vibe coding...",
                        color = TerminalGray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                items(messages) { message ->
                    TerminalMessageItem(message)
                }
            }
        }

        // Glowing Prompt Input Console Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .border(1.dp, color = DarkBorder)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                color = TerminalCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            OutlinedTextField(
                value = inputText,
                onValueChange = { if (!isProcessing) inputText = it },
                placeholder = { Text("vibe_instruction --force", color = TerminalGray.copy(alpha = 0.5f), fontSize = 13.sp) },
                singleLine = false,
                maxLines = 3,
                enabled = !isProcessing,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalWhite
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                )
            )

            Button(
                onClick = {
                    if (inputText.trim().isNotEmpty()) {
                        onSendPrompt(inputText)
                        inputText = ""
                    }
                },
                enabled = !isProcessing && inputText.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalGreen,
                    disabledContainerColor = TerminalGreen.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "RUN",
                    color = if (isProcessing) TerminalGray else DarkBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
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
                Text(
                    text = message.text,
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
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.type == MessageType.TOOL_OUTPUT) TerminalGreenDim else TerminalWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
