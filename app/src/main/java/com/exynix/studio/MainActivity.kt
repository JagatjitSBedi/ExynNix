package com.exynix.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.exynix.studio.ui.screens.*
import com.exynix.studio.ui.theme.*
import com.exynix.studio.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExynNixTheme {
                ExynNixApp()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard  : Screen("dashboard",  "Dashboard",  Icons.Default.Dashboard)
    object Models     : Screen("models",     "Models",     Icons.Default.Memory)
    object Chat       : Screen("chat",       "Chat",       Icons.Default.Chat)
    object Benchmark  : Screen("benchmark",  "Benchmark",  Icons.Default.Speed)
    object Settings   : Screen("settings",   "Settings",   Icons.Default.Settings)
}

val topLevelScreens = listOf(
    Screen.Dashboard,
    Screen.Models,
    Screen.Chat,
    Screen.Benchmark,
    Screen.Settings
)

@Composable
fun ExynNixApp() {
    val vm: MainViewModel = hiltViewModel()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    Scaffold(
        containerColor = Surface0,
        bottomBar = {
            NavigationBar(
                containerColor = Surface1,
                tonalElevation = 0.dp
            ) {
                topLevelScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ExBlue,
                            selectedTextColor = ExBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = ExBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface0)
                .padding(padding)
        ) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    vm = vm,
                    onNavigateToChat = { currentScreen = Screen.Chat },
                    onNavigateToModels = { currentScreen = Screen.Models }
                )
                Screen.Models -> ModelManagerScreen(
                    vm = vm,
                    onNavigateToChat = { currentScreen = Screen.Chat }
                )
                Screen.Chat -> ChatScreen(vm = vm)
                Screen.Benchmark -> BenchmarkScreen(vm = vm)
                Screen.Settings -> SettingsScreen(vm = vm)
            }
        }
    }
}
