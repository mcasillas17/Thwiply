package thwiply.elopenmike.com.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** Navigation never depends on model availability or a download completion event. */
@Composable
fun AppNavigation(
    mainScreen: @Composable (openSetup: () -> Unit) -> Unit,
    setupScreen: @Composable (returnToShell: () -> Unit) -> Unit,
) {
    val navController = rememberNavController()
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
