package com.example.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object NotebookEditor : Screen("editor/{notebookId}") {
        fun createRoute(notebookId: Long) = "editor/$notebookId"
    }
    object Export : Screen("export/{notebookId}") {
        fun createRoute(notebookId: Long) = "export/$notebookId"
    }
    object Settings : Screen("settings")
}
