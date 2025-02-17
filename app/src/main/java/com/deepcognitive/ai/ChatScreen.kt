package com.deepcognitive.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean // true for user's message; false for server response
)

@Composable
fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) Color.Blue.copy(alpha = 0.6f) else Color.LightGray.copy(alpha = 0.6f)
    val textColor = Color.Black  // Set text color to black for both user and server messages
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(8.dp),
                color = textColor,  // Force black text color
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ChatScreen() {
    var message by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedAI by remember { mutableStateOf("Top AI") } // Default selection
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val backgroundColor = Brush.verticalGradient(
        colors = listOf(Color(0xFFD4C49E), Color(0xFF9E9870))
    )

    val headerTextColor = Color.Black
    val translucentGrey = Color(0xBBE2E2E2)
    val outerTranslucentGrey = Color(0x99E2E2E2)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Header and chat history remain the same...
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.final_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(300.dp)
                    .offset(y = (-50).dp)
            )
            Text(
                text = "WHAT'S THE PROBLEM?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = headerTextColor,
                modifier = Modifier.offset(y = (-130).dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 220.dp)
                .imePadding(),
            contentPadding = PaddingValues(bottom = 200.dp)
        ) {
            items(chatHistory) { chat ->
                ChatBubble(chat)
            }
        }

        // Outer translucent container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = outerTranslucentGrey,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Text input field
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = translucentGrey,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.Black,
                            fontSize = 16.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (message.isEmpty()) {
                                    Text(
                                        "Type a message...",
                                        color = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Button row with dropdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = translucentGrey,
                            modifier = Modifier.height(40.dp)
                        ) {
                            IconButton(
                                onClick = { /* Add attachment functionality */ }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add attachment",
                                    tint = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box {
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                color = translucentGrey,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedAI,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { isDropdownExpanded = true }
                                    ) {
                                        Icon(
                                            painter = painterResource(id = android.R.drawable.arrow_down_float),
                                            contentDescription = "AI options",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier.background(translucentGrey)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Top AI") },
                                    onClick = {
                                        selectedAI = "Top AI"
                                        isDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bottom AI") },
                                    onClick = {
                                        selectedAI = "Bottom AI"
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = translucentGrey,
                        modifier = Modifier.height(40.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (message.isNotBlank()) {
                                    chatHistory = chatHistory + ChatMessage(message, isUser = true)
                                    message = ""
                                    keyboardController?.hide()
                                    coroutineScope.launch {
                                        delay(1000)
                                        chatHistory = chatHistory + ChatMessage("Server response", isUser = false)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Send",
                                tint = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ChatScreen()
}