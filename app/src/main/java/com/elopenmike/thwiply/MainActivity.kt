package com.elopenmike.thwiply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elopenmike.thwiply.ui.debug.DebugScreen
import com.elopenmike.thwiply.ui.onboarding.OnboardingScreen
import com.elopenmike.thwiply.ui.theme.ThwiplyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThwiplyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "onboarding") {
                        composable("onboarding") {
                            OnboardingScreen(onDownloadComplete = {
                                navController.navigate("debug") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            })
                        }
                        composable("debug") {
                            DebugScreen()
                        }
                    }
                }
            }
        }
    }
}
