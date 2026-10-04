package com.noteVE.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noteVE.domain.Settings
import com.noteVE.ui.theme.NoteAppTheme

@Composable
fun NoteApp(initialNoteId: Long?) {
    val theme by Settings.theme.collectAsState()
    val dynamicColorSetting by Settings.dynamicColor.collectAsState()
    val darkTheme = when (theme) {
        Settings.THEME_LIGHT -> false
        Settings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    NoteAppTheme(darkTheme = darkTheme, dynamicColor = dynamicColorSetting) {
        // 启动动画：先播放 VE 标志，结束后进入主界面
        var showSplash by remember { mutableStateOf(true) }
        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
            return@NoteAppTheme
        }
        val nav = rememberNavController()
        // 页面转场动画（参考 Vector：短促、克制、tween 驱动）
        NavHost(
            navController = nav,
            startDestination = "list",
            enterTransition = {
                slideInHorizontally(animationSpec = tween(240)) { it / 5 } + fadeIn(tween(200))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(200)) { -it / 8 } + fadeOut(tween(160))
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(240)) { -it / 8 } + fadeIn(tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(200)) { it / 5 } + fadeOut(tween(160))
            }
        ) {
            composable("list") {
                NoteListScreen(
                    onOpenNote = { nav.navigate("edit/$it") },
                    onNewNote = { nav.navigate("edit/new") },
                    onLicenses = { nav.navigate("licenses") },
                    onSettings = { nav.navigate("settings") },
                    onAbout = { nav.navigate("about") },
                    onStorage = { nav.navigate("storage") }
                )
            }
            composable("edit/new") {
                NoteEditScreen(noteId = null, onBack = { nav.popBackStack() })
            }
            composable(
                "edit/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("noteId") ?: 0L
                NoteEditScreen(noteId = id, onBack = { nav.popBackStack() })
            }
            composable("licenses") {
                LicenseScreen(onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onPermissions = { nav.navigate("permissions") }
                )
            }
            composable("permissions") {
                PermissionScreen(onBack = { nav.popBackStack() })
            }
            composable("about") {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable("storage") {
                StorageScreen(
                    onBack = { nav.popBackStack() },
                    onOpenNote = { id ->
                        nav.popBackStack()          // 先回列表，再进编辑
                        nav.navigate("edit/$id")
                    }
                )
            }
        }
        LaunchedEffect(initialNoteId) {
            if (initialNoteId != null) nav.navigate("edit/$initialNoteId")
        }
    }
}
