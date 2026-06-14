package com.inferno.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.inferno.gallery.ui.NavigationGraph
import com.inferno.gallery.ui.theme.PhotonGalleryTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.viewModels
import com.inferno.gallery.ui.ThemeMode
import com.inferno.gallery.ui.SettingsViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Force display to its peak supported refresh rate (90Hz, 120Hz, etc.) for fluid scrolling
        try {
            val peakRefreshRate = display?.supportedModes
                ?.map { it.refreshRate }
                ?.maxOrNull() ?: 60f
            if (peakRefreshRate > 60f) {
                val layoutParams = window.attributes
                try {
                    val minField = layoutParams.javaClass.getField("preferredMinDisplayRefreshRate")
                    val maxField = layoutParams.javaClass.getField("preferredMaxDisplayRefreshRate")
                    minField.set(layoutParams, peakRefreshRate)
                    maxField.set(layoutParams, peakRefreshRate)
                    window.attributes = layoutParams
                } catch (noSuchField: NoSuchFieldException) {
                    // Fallback for older devices/APIs if fields don't exist
                    android.util.Log.w("MainActivity", "preferredMinDisplayRefreshRate fields not found on this SDK version")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to force peak refresh rate", e)
        }

        enableEdgeToEdge()

        setContent {
            val isLoading by settingsViewModel.isLoading.collectAsState()
            splashScreen.setKeepOnScreenCondition { isLoading }
            
            val useFullScreen by settingsViewModel.useFullScreen.collectAsState()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val useMaterialYou by settingsViewModel.useMaterialYou.collectAsState()
            val useAmoledBlack by settingsViewModel.useAmoledBlack.collectAsState()
            
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LaunchedEffect(isDark, useFullScreen) {
                val style = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)

                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (useFullScreen) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            PhotonGalleryTheme(
                darkTheme = isDark,
                dynamicColor = useMaterialYou,
                useAmoledBlack = useAmoledBlack
            ) {
                val backgroundColor = MaterialTheme.colorScheme.background

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    NavigationGraph(
                        isLoading = isLoading,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
