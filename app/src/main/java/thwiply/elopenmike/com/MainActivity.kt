package thwiply.elopenmike.com

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import thwiply.elopenmike.com.ui.main.AppNavigation
import thwiply.elopenmike.com.ui.main.MainAppScreen
import thwiply.elopenmike.com.ui.onboarding.OnboardingScreen
import thwiply.elopenmike.com.ui.onboarding.OnboardingViewModel
import thwiply.elopenmike.com.ui.theme.ThemeManager
import thwiply.elopenmike.com.ui.theme.ThwiplyTheme
import thwiply.elopenmike.com.llm.provider.InferenceCoordinator
import javax.inject.Inject
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.flow.map

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
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        setContent {
            val themeMode by themeManager.themeMode.collectAsStateWithLifecycle()
            val foldingFeatures by remember {
                WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)
                    .map { it.displayFeatures.filterIsInstance<FoldingFeature>() }
            }.collectAsStateWithLifecycle(initialValue = emptyList())

            ThwiplyTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        foldingFeatures = foldingFeatures,
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
