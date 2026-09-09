package thwiply.elopenmike.com.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.IntRect
import androidx.window.layout.FoldingFeature
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** Navigation never depends on model availability or a download completion event. */
@Composable
fun AppNavigation(
    mainScreen: @Composable (openSetup: () -> Unit) -> Unit,
    setupScreen: @Composable (returnToShell: () -> Unit) -> Unit,
    foldingFeatures: List<FoldingFeature> = emptyList(),
) {
    val navController = rememberNavController()
    val obstructions = foldingFeatures
        .filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
        .map { it.bounds.let { bounds -> IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom) } }
    CompositionLocalProvider(LocalFoldingBounds provides obstructions) {
        AppViewport {
            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    mainScreen {
                        navController.navigate("model-setup") { launchSingleTop = true }
                    }
                }
                composable("model-setup") {
                    setupScreen { navController.popBackStack("main", inclusive = false) }
                }
            }
        }
    }
}
