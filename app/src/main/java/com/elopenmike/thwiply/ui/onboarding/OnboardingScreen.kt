package com.elopenmike.thwiply.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elopenmike.thwiply.llm.model.DownloadState

@Composable
fun OnboardingScreen(
    onDownloadComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var url by remember { mutableStateOf("https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm?download=true") }
    var token by remember { mutableStateOf("") }
    
    LaunchedEffect(state) {
        if (state is DownloadState.Success) onDownloadComplete()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Thwiply", style = MaterialTheme.typography.headlineLarge)
        Text("We need to download the AI model.")
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Model URL") })
        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("HF Token (if gated)") })
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (state) {
            is DownloadState.Idle -> {
                Button(onClick = { viewModel.startDownload(url, token) }) { Text("Start Download") }
            }
            is DownloadState.Downloading -> {
                val progress = (state as DownloadState.Downloading).progress
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Downloading... $progress%")
            }
            is DownloadState.Error -> {
                Text("Error: ${(state as DownloadState.Error).message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.startDownload(url, token) }) { Text("Retry") }
            }
            is DownloadState.Success -> {
                Text("Download Complete!")
            }
        }
    }
}
