package thwiply.elopenmike.com.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import thwiply.elopenmike.com.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import thwiply.elopenmike.com.ui.playground.PlaygroundScreen
import thwiply.elopenmike.com.ui.settings.SettingsScreen
import thwiply.elopenmike.com.ui.theme.ElectricCyanAccent
import thwiply.elopenmike.com.ui.today.TodayScreen

enum class MainTab(
    @StringRes val title: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY(R.string.tab_today, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    LAB(R.string.tab_lab, Icons.Filled.Bolt, Icons.Outlined.Bolt),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainAppScreen(onModelSetup: () -> Unit) {
    MainAppContent { tab ->
        when (tab) {
            MainTab.TODAY -> TodayScreen()
            MainTab.LAB -> PlaygroundScreen(onModelSetup = onModelSetup)
            MainTab.SETTINGS -> SettingsScreen(onModelSetup = onModelSetup)
        }
    }
}

@Composable
fun MainAppContent(content: @Composable (MainTab) -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    val tabState = rememberSaveableStateHolder()
    val compactHeight = LocalCompactHeight.current

    Scaffold(
        bottomBar = {
            if (compactHeight) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().selectableGroup()) {
                        MainTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            Box(
                                Modifier.weight(1f).heightIn(min = 48.dp)
                                    .selectable(
                                        selected = selected,
                                        role = Role.Tab,
                                        onClick = { selectedTab = tab },
                                    )
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(tab.title),
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            } else {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    MainTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.title),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            tabState.SaveableStateProvider(selectedTab) {
                content(selectedTab)
            }
        }
    }
}
