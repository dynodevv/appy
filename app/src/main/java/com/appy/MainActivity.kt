package com.appy

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appy.data.SettingsRepository
import com.appy.processor.ApkProcessor
import com.appy.processor.ApkProcessingResult
import com.appy.ui.screens.BuildState
import com.appy.ui.screens.HomeScreen
import com.appy.ui.screens.SettingsScreen
import com.appy.ui.screens.ThemeMode
import com.appy.ui.theme.AppyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var apkProcessor: ApkProcessor
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Enable high refresh rate (120Hz) for smoother animations
        enableHighRefreshRate()

        apkProcessor = ApkProcessor(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)

        setContent {
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val materialYouEnabled by settingsRepository.materialYouFlow.collectAsState(initial = true)
            val scope = rememberCoroutineScope()
            
            val isDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            
            // Update status bar appearance based on theme
            LaunchedEffect(isDarkTheme) {
                updateStatusBarAppearance(isDarkTheme)
            }
            
            AppyTheme(
                darkTheme = isDarkTheme,
                dynamicColor = materialYouEnabled
            ) {
                val navController = rememberNavController()
                var buildState by remember { mutableStateOf<BuildState>(BuildState.Idle) }
                var pendingTempFilePath by remember { mutableStateOf<String?>(null) }

                // File picker launcher for saving APK
                val saveFileLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
                ) { uri: Uri? ->
                    if (uri != null && pendingTempFilePath != null) {
                        scope.launch {
                            when (val result = apkProcessor.saveApkToUri(pendingTempFilePath!!, uri)) {
                                is ApkProcessor.SaveResult.Success -> {
                                    buildState = BuildState.Success
                                    Toast.makeText(
                                        this@MainActivity,
                                        "APK saved successfully!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is ApkProcessor.SaveResult.Error -> {
                                    buildState = BuildState.Error("Failed to save APK: ${result.message}")
                                }
                            }
                            pendingTempFilePath = null
                        }
                    } else {
                        // User cancelled the file picker
                        buildState = BuildState.Idle
                        pendingTempFilePath = null
                    }
                }

                // Launch file picker when APK is ready to save
                LaunchedEffect(buildState) {
                    if (buildState is BuildState.ReadyToSave) {
                        val readyState = buildState as BuildState.ReadyToSave
                        pendingTempFilePath = readyState.tempFilePath
                        saveFileLauncher.launch(readyState.suggestedFileName)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "home",
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(200)
                        ) + fadeIn(animationSpec = tween(150))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(200)
                        ) + fadeOut(animationSpec = tween(150))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(200)
                        ) + fadeIn(animationSpec = tween(150))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(200)
                        ) + fadeOut(animationSpec = tween(150))
                    }
                ) {
                    composable("home") {
                        HomeScreen(
                            buildState = buildState,
                            onBuildClick = { config ->
                                scope.launch {
                                    buildState = BuildState.Building(0f, "Starting...")

                                    apkProcessor.generateApk(
                                        url = config.url,
                                        appName = config.appName,
                                        packageId = config.packageId,
                                        iconUri = config.iconUri,
                                        statusBarDark = config.statusBarStyle == com.appy.ui.screens.StatusBarStyle.DARK
                                    ).collect { result ->
                                        when (result) {
                                            is ApkProcessingResult.Progress -> {
                                                buildState = BuildState.Building(
                                                    result.progress,
                                                    result.message
                                                )
                                            }
                                            is ApkProcessingResult.ReadyToSave -> {
                                                buildState = BuildState.ReadyToSave(
                                                    result.tempFilePath,
                                                    result.suggestedFileName
                                                )
                                            }
                                            is ApkProcessingResult.Error -> {
                                                buildState = BuildState.Error(result.message)
                                            }
                                        }
                                    }
                                }
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            currentThemeMode = themeMode,
                            onThemeModeChange = { newMode ->
                                scope.launch {
                                    settingsRepository.setThemeMode(newMode)
                                }
                            },
                            materialYouEnabled = materialYouEnabled,
                            onMaterialYouChange = { enabled ->
                                scope.launch {
                                    settingsRepository.setMaterialYouEnabled(enabled)
                                }
                            },
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Updates the status bar appearance (icon color) based on the current theme.
     * For light themes, uses dark icons. For dark themes, uses light icons.
     * Also sets up appropriate scrim colors for predictive back gesture.
     */
    @Suppress("DEPRECATION")
    private fun updateStatusBarAppearance(isDarkTheme: Boolean) {
        // Set window background color to match theme for predictive back gesture scrim
        // This affects the "reveal" color during the back gesture animation
        if (isDarkTheme) {
            window.decorView.setBackgroundColor(Color.parseColor("#1C1B1F")) // Dark background
            window.setNavigationBarColor(Color.parseColor("#1C1B1F"))
        } else {
            window.decorView.setBackgroundColor(Color.parseColor("#FEFBFF")) // Light background
            window.setNavigationBarColor(Color.parseColor("#FEFBFF"))
        }
        
        // Update edge-to-edge with appropriate system bar styles for the theme
        if (isDarkTheme) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                if (isDarkTheme) {
                    // Dark theme: clear the light appearance flag (use light/white icons)
                    controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or 
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                } else {
                    // Light theme: set light appearance flag (use dark icons)
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        } else {
            // For older APIs
            if (isDarkTheme) {
                window.decorView.systemUiVisibility = 
                    window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                window.decorView.systemUiVisibility = 
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
    
    /**
     * Enables high refresh rate (up to 120Hz) for smoother animations.
     * This ensures the app runs at the device's maximum refresh rate.
     */
    private fun enableHighRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Request unlimited frame rate
            window.attributes.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Request the highest available refresh rate
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            
            display?.let { d ->
                val supportedModes = d.supportedModes
                val highestRefreshMode = supportedModes.maxByOrNull { it.refreshRate }
                
                highestRefreshMode?.let { mode ->
                    val params = window.attributes
                    params.preferredDisplayModeId = mode.modeId
                    window.attributes = params
                }
            }
        }
    }
}
