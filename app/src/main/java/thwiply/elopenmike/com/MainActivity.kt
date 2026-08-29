package thwiply.elopenmike.com

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
import dagger.hilt.android.AndroidEntryPoint
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.ui.main.MainAppScreen
import thwiply.elopenmike.com.ui.onboarding.OnboardingScreen
import thwiply.elopenmike.com.ui.theme.ThemeManager
import thwiply.elopenmike.com.ui.theme.ThwiplyTheme
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
                            MainAppScreen()
                        }
                    }
                }
            }
        }
    }
}
