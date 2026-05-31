package com.elopenmike.thwiply.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DebugScreen(viewModel: DebugViewModel = hiltViewModel()) {
    val isInit by viewModel.isInitializing.collectAsState()
    val output by viewModel.output.collectAsState()
    var prompt by remember { mutableStateOf("Hello!") }
    
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("LLM Debug Inference", style = MaterialTheme.typography.headlineMedium)
        
        if (isInit) {
            CircularProgressIndicator()
            Text("Initializing engine...")
        } else {
            OutlinedTextField(
                value = prompt, 
                onValueChange = { prompt = it }, 
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { viewModel.generate(prompt) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Generate")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Output:")
            Text(output, modifier = Modifier.weight(1f))
        }
    }
}
