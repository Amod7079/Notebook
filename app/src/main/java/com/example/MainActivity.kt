package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.database.AppDatabase
import com.example.data.repository.NotebookRepository
import com.example.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NotebookViewModel
import com.example.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core component initialization (SQLite local persistence + repositories)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = NotebookRepository(
            notebookDao = database.notebookDao(),
            pageDao = database.pageDao(),
            strokeDao = database.strokeDao(),
            settingDao = database.settingDao()
        )
        
        // AndroidViewModel instantiation via standard ViewModelProvider.Factory
        val viewModel: NotebookViewModel by viewModels {
            ViewModelFactory(application, repository)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            
            // Handle Light, Dark, or automatic Android System adaptive theme options
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                
                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            onNavigateToHome = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToEditor = { notebookId ->
                                navController.navigate(Screen.NotebookEditor.createRoute(notebookId))
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                    }
                    composable(
                        route = Screen.NotebookEditor.route,
                        arguments = listOf(navArgument("notebookId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val notebookId = backStackEntry.arguments?.getLong("notebookId") ?: 0L
                        NotebookEditorScreen(
                            viewModel = viewModel,
                            notebookId = notebookId,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToExport = { id ->
                                navController.navigate(Screen.Export.createRoute(id))
                            }
                        )
                    }
                    composable(
                        route = Screen.Export.route,
                        arguments = listOf(navArgument("notebookId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val notebookId = backStackEntry.arguments?.getLong("notebookId") ?: 0L
                        ExportScreen(
                            viewModel = viewModel,
                            notebookId = notebookId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
