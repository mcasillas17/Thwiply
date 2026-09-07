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
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import thwiply.elopenmike.com.ui.main.AppNavigation
import thwiply.elopenmike.com.ui.main.MainAppScreen
import thwiply.elopenmike.com.ui.onboarding.OnboardingScreen
import thwiply.elopenmike.com.ui.onboarding.OnboardingViewModel
import thwiply.elopenmike.com.ui.theme.ThemeManager
import thwiply.elopenmike.com.ui.theme.ThwiplyTheme
import thwiply.elopenmike.com.llm.provider.InferenceCoordinator
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var inferenceCoordinator: InferenceCoordinator

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        inferenceCoordinator.setForeground(isTopResumedActivity)
    }

    override fun onPause() {
        inferenceCoordinator.setForeground(false)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by themeManager.themeMode.collectAsState()

            ThwiplyTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        mainScreen = { openSetup -> MainAppScreen(onModelSetup = openSetup) },
                        setupScreen = { returnToShell ->
                            // Create model setup only when requested. Its activity owner
                            // survives rotation and repeated visits without overlapping writers.
                            val setupViewModel: OnboardingViewModel = hiltViewModel(this@MainActivity)
                            OnboardingScreen(
                                onExit = {
                                    setupViewModel.pauseDownload()
                                    returnToShell()
                                },
                                viewModel = setupViewModel,
                            )
                        },
                    )
                }
            }
        }
    }
}
