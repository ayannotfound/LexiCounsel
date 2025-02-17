package com.deepcognitive.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.deepcognitive.ai.ui.theme.DeepCognitiveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeepCognitiveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen() // Ensure ChatScreen is being called here
                }
            }
        }
    }
}

