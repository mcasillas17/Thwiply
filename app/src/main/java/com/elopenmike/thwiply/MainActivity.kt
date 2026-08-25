package com.elopenmike.thwiply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elopenmike.thwiply.llm.model.ModelManager
import com.elopenmike.thwiply.ui.main.MainAppScreen
import com.elopenmike.thwiply.ui.onboarding.OnboardingScreen
import com.elopenmike.thwiply.ui.theme.ThemeManager
import com.elopenmike.thwiply.ui.theme.ThwiplyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by themeManager.themeMode.collectAsState()

            ThwiplyTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDest = if (modelManager.isModelAvailable()) "main" else "onboarding"

                    NavHost(navController = navController, startDestination = startDest) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onDownloadComplete = {
                                    navController.navigate("main") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("main") {
                            MainAppScreen(
                                onNavigateToOnboarding = {
                                    navController.navigate("onboarding")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
