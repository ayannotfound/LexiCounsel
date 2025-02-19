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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocketListener
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.io.BufferedReader
import java.io.InputStreamReader
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

// === Data Models ===
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val imageUri: Uri? = null // Add support for image responses
)

// === API Service Definitions ===
interface TextAIService {
    @POST("generate")
    suspend fun generateText(@Body request: TextAIRequest): TextAIResponse
}

data class TextAIRequest(
    val prompt: String,
    val model: String = "DolphinLlama1b",
    val temperature: Int = 1,
    val max_tokens: Int = 500
)

data class TextAIResponse(
    @SerializedName("response") val text: String,
    val done: Boolean = true
)

data class WebSocketResponse(
    val type: String,
    val content: String = ""
)

// === Streaming Text Listener ===
interface TextStreamListener {
    fun onToken(token: String)
    fun onComplete()
    fun onError(error: Throwable)
}

// === AI Service Manager ===
class AIServiceManager(baseUrl: String) {
    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val textAIService: TextAIService = retrofit.create(TextAIService::class.java)

    // WebSocket for streaming implementation
    private var ws: okhttp3.WebSocket? = null
    private val _textStream = MutableStateFlow<String>("")
    val textStream = _textStream.asStateFlow()

    fun connectWebSocket(url: String, listener: TextStreamListener) {
        // Remove any trailing slash from the base URL
        val cleanedBaseUrl = url.trimEnd('/')
        // Build the WebSocket URL only if it isn't already set with the expected path
        val wsUrl = if (cleanedBaseUrl.endsWith("/ws")) {
            cleanedBaseUrl
        } else {
            when {
                cleanedBaseUrl.startsWith("ws://") || cleanedBaseUrl.startsWith("wss://") -> cleanedBaseUrl
                cleanedBaseUrl.startsWith("http://") -> cleanedBaseUrl.replace("http://", "ws://")
                cleanedBaseUrl.startsWith("https://") -> cleanedBaseUrl.replace("https://", "wss://")
                else -> "ws://$cleanedBaseUrl"
            } + "/ws"
        }

        Log.d("WebSocket", "Connecting to $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val wsListener = object : WebSocketListener() {
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                try {
                    val response = Gson().fromJson(text, WebSocketResponse::class.java)
                    when (response.type) {
                        "token" -> {
                            _textStream.value += response.content
                            listener.onToken(response.content)
                        }
                        "done" -> listener.onComplete()
                        "error" -> {
                            // Optionally log error response, but do not send message to chat UI
                            Log.e("WebSocket", "Error from server: ${response.content}")
                        }
                    }
                } catch (e: Exception) {
                    // Log error for debugging
                    Log.e("WebSocket", "Error parsing response", e)
                }
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                // Log the error and do not propagate to UI
                Log.e("WebSocket", "Connection failed", t)
                // Do not call listener.onError to avoid showing error message in chat
            }
        }

        ws = client.newWebSocket(request, wsListener)
    }

    fun sendTextPrompt(prompt: String, model: String = "DolphinLlama1b") {
        ws?.send(Gson().toJson(
            mapOf(
                "prompt" to prompt,
                "model" to model,
                "temperature" to 1,
                "max_tokens" to 500
            )
        ))
    }

    fun disconnect() {
        ws?.close(1000, "User closed connection")
        ws = null
    }
}

// === UI Components ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewDialog(
    imageUri: Uri,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        properties = DialogProperties(
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Close button - positioned above the image
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .zIndex(1f)  // Ensure button stays on top
                        .offset(x = (-12).dp, y = (-12).dp)  // Move button diagonally
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Image
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Enlarged image",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    )
}

@Composable
fun ChatBubble(message: ChatMessage) {
    var showImagePreview by remember { mutableStateOf(false) }
    val bubbleColor = if (message.isUser) Color.Blue.copy(alpha = 0.25f) else Color.LightGray.copy(alpha = 0.3f)
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val maxWidth = 300.dp

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier.padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .widthIn(max = maxWidth)
            ) {
                // Show image if present
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Shared image",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable { showImagePreview = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // Show text if present
                if (message.text.isNotEmpty()) {
                    Text(
                        text = message.text,
                        color = Color.Black,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    // Show preview dialog when image is clicked
    if (showImagePreview && message.imageUri != null) {
        ImagePreviewDialog(
            imageUri = message.imageUri,
            onDismiss = { showImagePreview = false }
        )
    }
}

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var apiUrl by remember { mutableStateOf("") }
    var selectedAI by remember { mutableStateOf("Text") }
    var message by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var hasStartedChat by remember { mutableStateOf(false) }
    
    // Helper function moved up
    fun displayMessage(message: String, isError: Boolean = false) {
        val displayText = if (isError) {
            message
        } else {
            message
        }
        chatHistory = chatHistory + ChatMessage(displayText, isUser = false)
    }

    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val client = remember { OkHttpClient() }

    // Add AI service manager
    val aiServiceManager = remember(apiUrl) {
        if (apiUrl.isNotBlank()) {
            val baseUrl = if (apiUrl.startsWith("http")) apiUrl else "http://$apiUrl:8000"
            AIServiceManager(baseUrl)
        } else null
    }

    // Add state for streaming text responses
    var isStreaming by remember { mutableStateOf(false) }
    var streamedText by remember { mutableStateOf("") }

    // Create a TextStreamListener
    val textStreamListener = remember {
        object : TextStreamListener {
            override fun onToken(token: String) {
                streamedText += token
            }

            override fun onComplete() {
                isStreaming = false
                chatHistory = chatHistory + ChatMessage(streamedText, isUser = false)
                streamedText = ""
            }

            override fun onError(error: Throwable) {
                isStreaming = false
                Log.e("WebSocket", "Error: ${error.message}", error)
                chatHistory = chatHistory + ChatMessage(
                    "Error: ${error.message ?: "Unknown error"}",
                    isUser = false
                )
                streamedText = ""
            }
        }
    }

    // Modify the WebSocket connection logic
    LaunchedEffect(aiServiceManager, selectedAI) {
        if (apiUrl.isNotBlank() && (selectedAI == "Text" || selectedAI == "Top AI")) {
            try {
                // Add a loading message while connecting
                if (!hasStartedChat) {
                    displayMessage("Connecting to AI service...", isError = false)
                }
                
                aiServiceManager?.connectWebSocket(apiUrl, textStreamListener)
                
                // Remove the loading message once connected
                if (!hasStartedChat) {
                    chatHistory = chatHistory.dropLast(1)
                }
                
                Log.d("WebSocket", "Connected to WebSocket at $apiUrl")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to connect to WebSocket", e)
                displayMessage("Failed to connect to AI service. Please check your connection.", isError = true)
            }
        }
    }

    // Disconnect WebSocket when the component is destroyed
    DisposableEffect(Unit) {
        onDispose {
            aiServiceManager?.disconnect()
        }
    }

    // Auto-scroll when chatHistory size or streamedText changes
    LaunchedEffect(chatHistory.size, streamedText, isStreaming) {
        // Calculate the total number of items; if streaming text is present, it'll be added as an extra item
        val itemCount = chatHistory.size + if (isStreaming && streamedText.isNotEmpty()) 1 else 0
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    val translucentGrey = Color(0xBBE2E2E2)
    val outerTranslucentGrey = Color(0x99E2E2E2)

    suspend fun extractTextFromFile(context: Context, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    uri.toString().endsWith(".pdf", ignoreCase = true) -> {
                        PDFBoxResourceLoader.init(context)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val document = PDDocument.load(inputStream)
                            val stripper = PDFTextStripper().apply {
                                // Set reading properties
                                sortByPosition = true
                                addMoreFormatting = false
                            }
                            val rawText = stripper.getText(document)
                            document.close()
                            
                            // Clean the text: only allow basic ASCII characters, numbers, and common punctuation
                            rawText.replace(Regex("[^\\x20-\\x7E\\n]"), "")
                                  .replace(Regex("\\s+"), " ")
                                  .trim()
                        } ?: throw IOException("Could not open PDF file")
                    }
                    else -> {
                        // Handle text files
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                                lines.joinToString("\n")
                            }
                        } ?: throw IOException("Could not open text file")
                    }
                }
            } catch (e: Exception) {
                throw IOException("Error reading file: ${e.message}")
            }
        }
    }

    // Attachment launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            chatHistory = chatHistory + ChatMessage("", isUser = true, imageUri = it)
        }
    }
    val textPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val text = extractTextFromFile(context, it)
                    chatHistory = chatHistory + ChatMessage(text, isUser = true)
                    
                    // Automatically send the extracted text
                    if (aiServiceManager != null) {
                        try {
                            isStreaming = true
                            streamedText = ""
                            aiServiceManager.sendTextPrompt(text)
                            hasStartedChat = true
                        } catch (e: Exception) {
                            Log.e("ChatScreen", "Error generating text response", e)
                            isStreaming = false
                            displayMessage(e.message ?: "Unknown error", isError = true)
                        }
                    } else {
                        displayMessage("Error: API service not initialized. Please check your connection settings.", isError = true)
                    }
                    
                    Toast.makeText(context, "Text extracted and sent", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val text = extractTextFromFile(context, it)
                    chatHistory = chatHistory + ChatMessage(text, isUser = true)
                    
                    // Automatically send the extracted text
                    if (aiServiceManager != null) {
                        try {
                            isStreaming = true
                            streamedText = ""
                            aiServiceManager.sendTextPrompt(text)
                            hasStartedChat = true
                        } catch (e: Exception) {
                            Log.e("ChatScreen", "Error generating text response", e)
                            isStreaming = false
                            displayMessage(e.message ?: "Unknown error", isError = true)
                        }
                    } else {
                        displayMessage("Error: API service not initialized. Please check your connection settings.", isError = true)
                    }
                    
                    Toast.makeText(context, "Text extracted from PDF and sent", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var cameraImageUri by remember { mutableStateOf<Uri?>(Uri.EMPTY) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && cameraImageUri != null) {
            chatHistory = chatHistory + ChatMessage("", isUser = true, imageUri = cameraImageUri)
        }
    }

    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }

    // Load the appropriate API URL based on selected AI type
    LaunchedEffect(selectedAI) {
        when (selectedAI) {
            "Text", "Top AI" -> dataStoreManager.textAiUrl.collect { url -> apiUrl = url }
            "Image Gen AI" -> dataStoreManager.imageAiUrl.collect { url -> apiUrl = url }
            "OCR" -> dataStoreManager.ocrAiUrl.collect { url -> apiUrl = url }
        }
    }

    // Ensure extractTextFromFile is defined before its usage


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
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFD4C49E), Color(0xFF9E9870))
                        )
                    )
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
                            painter = painterResource(id = R.drawable.lexicounsel_text),
                            contentDescription = "Logo",
                            modifier = Modifier.size(350.dp)
                        )
                        Text(
                            text = "WHAT'S THE PROBLEM?",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = (-100).dp)
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 25.dp)
                        .padding(bottom = 150.dp)
                        .imePadding(),
                    contentPadding = PaddingValues(bottom = 200.dp)
                ) {
                    items(chatHistory) { chat ->
                        ChatBubble(chat)
                    }

                    // If streaming, show the current streamed text
                    if (isStreaming && streamedText.isNotEmpty()) {
                        item {
                            ChatBubble(ChatMessage(streamedText, isUser = false))
                        }
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
                                                val currentMessage = message
                                                message = ""
                                                keyboardController?.hide()

                                                when (selectedAI) {
                                                    "Text", "Top AI" -> {
                                                        if (aiServiceManager != null) {
                                                            try {
                                                                // Add the user message immediately
                                                                chatHistory = chatHistory + ChatMessage(currentMessage, isUser = true)
                                                                hasStartedChat = true
                                                                
                                                                // Start streaming immediately
                                                                isStreaming = true
                                                                streamedText = ""
                                                                aiServiceManager.sendTextPrompt(currentMessage)
                                                            } catch (e: Exception) {
                                                                Log.e("ChatScreen", "Error generating text response", e)
                                                                isStreaming = false
                                                                displayMessage(e.message ?: "Unknown error", isError = true)
                                                            }
                                                        } else {
                                                            displayMessage("Error: AI service not initialized. Please check your connection settings.", isError = true)
                                                        }
                                                    }
                                                    "Image Gen AI" -> {
                                                        try {
                                                            val imageUri = generateImage(context, currentMessage)
                                                            if (imageUri != null) {
                                                                chatHistory = chatHistory + ChatMessage("", isUser = false, imageUri = imageUri)
                                                                hasStartedChat = true
                                                            } else {
                                                                displayMessage("Failed to generate image. Please check your connection and try again.", isError = true)
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("ChatScreen", "Error generating image", e)
                                                            displayMessage(e.message ?: "Unknown error", isError = true)
                                                        }
                                                    }
                                                    else -> {
                                                        delay(1000)
                                                        displayMessage("Server response", isError = false)
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
                                text = { Text("Text") },
                                onClick = {
                                    selectedAI = "Text"
                                    isDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Websearch") },
                                onClick = {
                                    selectedAI = "Websearch"
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



            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ChatScreen()
}