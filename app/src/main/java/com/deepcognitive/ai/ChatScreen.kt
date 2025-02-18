package com.deepcognitive.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val imageUri: Uri? = null // Add support for image responses
)

@Composable
fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) Color.Blue.copy(alpha = 0.4f) else Color.LightGray.copy(alpha = 0.3f)
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
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = message.text,
                    color = Color.Black,
                    fontSize = 16.sp
                )
                message.imageUri?.let { uri ->
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = uri,
                        contentDescription = "Generated image",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var apiUrl by remember { mutableStateOf("") }
    var isDialogOpen by remember { mutableStateOf(false) }

    // Load the saved IP address
    LaunchedEffect(Unit) {
        dataStoreManager.ipAddress.collect { savedIp ->
            apiUrl = savedIp
        }
    }

    var message by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedAI by remember { mutableStateOf("Top AI") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val client = remember { OkHttpClient() }


    // Auto-scroll to the last message when chatHistory changes
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    val translucentGrey = Color(0xBBE2E2E2)
    val outerTranslucentGrey = Color(0x99E2E2E2)

    // Attachment launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            chatHistory = chatHistory + ChatMessage("Image selected: $uri", isUser = true)
        }
    }
    val textPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            chatHistory = chatHistory + ChatMessage("Text file selected: $uri", isUser = true)
        }
    }
    val jsonPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            chatHistory = chatHistory + ChatMessage("JSON file selected: $uri", isUser = true)
        }
    }
    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            chatHistory = chatHistory + ChatMessage("PDF file selected: $uri", isUser = true)
        }
    }
    var cameraImageUri by remember { mutableStateOf<Uri?>(Uri.EMPTY) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && cameraImageUri != null) {
            chatHistory = chatHistory + ChatMessage("Photo taken: $cameraImageUri", isUser = true)
        }
    }

    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }

    // Add state to track if first message has been sent
    var hasStartedChat by remember { mutableStateOf(false) }

    suspend fun generateImage(context: Context, text: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("Network", "Starting request to: $apiUrl")

                // Create form body
                val formBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("text", text)
                    .build()

                // Create request
                val request = Request.Builder()
                    .url(apiUrl)
                    .post(formBody)
                    .build()

                // Execute request
                client.newCall(request).execute().use { response ->
                    Log.d("Network", "Response code: ${response.code}")

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        Log.e("Network", "Error response: $errorBody")
                        throw IOException("Request failed: ${response.code} $errorBody")
                    }

                    val responseBody = response.body ?: throw IOException("Empty response")

                    // Save to file
                    val file = File.createTempFile("generated_", ".jpg", context.cacheDir)
                    file.outputStream().use { outputStream ->
                        responseBody.byteStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    Log.d("Network", "File saved: ${file.length()} bytes")

                    if (file.length() == 0L) {
                        throw IOException("Generated file is empty")
                    }

                    Uri.fromFile(file)
                }
            } catch (e: Exception) {
                Log.e("Network", "Error: ${e.message}", e)
                null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasStartedChat) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Text("Chat")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("dashboard") }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Go to Dashboard")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color(0xFFD4C49E), Color(0xFF9E9870))))
                .padding(innerPadding)
        ) {
            // Only show the initial UI if chat hasn't started
            if (!hasStartedChat) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(350.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "WHAT'S THE PROBLEM?",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 25.dp)
                    .imePadding(),
                contentPadding = PaddingValues(bottom = 200.dp)
            ) {
                items(chatHistory) { chat ->
                    ChatBubble(chat)
                }
            }

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
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = translucentGrey,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BasicTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier
                                    .weight(1f)
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
                            IconButton(onClick = {
                                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                                    speechRecognizer.setRecognitionListener(object : RecognitionListener {
                                        override fun onReadyForSpeech(params: Bundle?) {}
                                        override fun onBeginningOfSpeech() {}
                                        override fun onRmsChanged(rmsdB: Float) {}
                                        override fun onBufferReceived(buffer: ByteArray?) {}
                                        override fun onEndOfSpeech() {}
                                        override fun onError(error: Int) {
                                            Toast.makeText(context, "Error recognizing speech", Toast.LENGTH_SHORT).show()
                                        }
                                        override fun onResults(results: Bundle?) {
                                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                            if (!matches.isNullOrEmpty()) {
                                                message = matches[0]
                                            }
                                        }
                                        override fun onPartialResults(partialResults: Bundle?) {}
                                        override fun onEvent(eventType: Int, params: Bundle?) {}
                                    })
                                    speechRecognizer.startListening(speechRecognizerIntent)
                                } else {
                                    Toast.makeText(context, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Filled.Mic, contentDescription = "Voice Input", tint = Color.Gray)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side buttons container
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Attachment Button
                            Surface(
                                shape = RoundedCornerShape(32.dp),
                                color = translucentGrey,
                                modifier = Modifier.height(40.dp)
                            ) {
                                IconButton(onClick = { isAttachmentMenuExpanded = true }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add attachment",
                                        tint = Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // AI Dropdown Button
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
                        }

                        // Send Button (right side)
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = translucentGrey,
                            modifier = Modifier.height(40.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (message.isNotBlank()) {
                                        coroutineScope.launch {
                                            hasStartedChat = true  // Set this when first message is sent
                                            val currentMessage = message
                                            message = ""
                                            keyboardController?.hide()

                                            chatHistory = chatHistory + ChatMessage(currentMessage, isUser = true)

                                            when (selectedAI) {
                                                "Image Gen AI" -> {
                                                    try {
                                                        val imageUri = generateImage(context, currentMessage)
                                                        if (imageUri != null) {
                                                            chatHistory = chatHistory + ChatMessage(
                                                                text = "Generated image for: $currentMessage",
                                                                isUser = false,
                                                                imageUri = imageUri
                                                            )
                                                        } else {
                                                            chatHistory = chatHistory + ChatMessage(
                                                                text = "Failed to generate image. Please check your connection and try again.",
                                                                isUser = false
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("ChatScreen", "Error generating image", e)
                                                        chatHistory = chatHistory + ChatMessage(
                                                            text = "Error: ${e.message}",
                                                            isUser = false
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    delay(1000)
                                                    chatHistory = chatHistory + ChatMessage(
                                                        text = "Server response",
                                                        isUser = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )  {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Send",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    // Dropdown menus
                    DropdownMenu(
                        expanded = isAttachmentMenuExpanded,
                        onDismissRequest = { isAttachmentMenuExpanded = false },
                        modifier = Modifier.background(translucentGrey)
                    ) {
                        listOf(
                            Triple("Select Image", "image/*", imagePickerLauncher),
                            Triple("Take Photo", null, cameraLauncher),
                            Triple("Select Text File", "text/plain", textPickerLauncher),
                            Triple("Select JSON File", "application/json", jsonPickerLauncher),
                            Triple("Select PDF File", "application/pdf", pdfPickerLauncher)
                        ).forEach { (text, mimeType, launcher) ->
                            DropdownMenuItem(
                                text = { Text(text) },
                                onClick = {
                                    isAttachmentMenuExpanded = false
                                    if (mimeType != null) {
                                        (launcher as ManagedActivityResultLauncher<String, Uri?>).launch(mimeType)
                                    } else {
                                        cameraImageUri?.let { (launcher as ManagedActivityResultLauncher<Uri, Boolean>).launch(it) }
                                    }
                                }
                            )
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
                        DropdownMenuItem(
                            text = { Text("Image Gen AI") },
                            onClick = {
                                selectedAI = "Image Gen AI"
                                isDropdownExpanded = false
                            }
                        )
                    }

                }
            }

            if (isDialogOpen) {
                AlertDialog(
                    onDismissRequest = { isDialogOpen = false },
                    title = { Text("Change API IP Address") },
                    text = {
                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = { apiUrl = it },
                            label = { Text("API IP Address") }
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            coroutineScope.launch {
                                dataStoreManager.saveIpAddress(apiUrl)
                                isDialogOpen = false
                            }
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { isDialogOpen = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    val navController = TestNavHostController(LocalContext.current)
    ChatScreen(navController = navController)
}