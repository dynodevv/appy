package com.appy

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.appy.processor.ApkProcessor
import com.appy.processor.ApkProcessingResult
import com.appy.ui.screens.BuildState
import com.appy.ui.screens.HomeScreen
import com.appy.ui.theme.AppyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var apkProcessor: ApkProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        apkProcessor = ApkProcessor(applicationContext)

        setContent {
            AppyTheme {
                var buildState by remember { mutableStateOf<BuildState>(BuildState.Idle) }
                var pendingTempFilePath by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

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

                HomeScreen(
                    buildState = buildState,
                    onBuildClick = { config ->
                        scope.launch {
                            buildState = BuildState.Building(0f, "Starting...")

                            apkProcessor.generateApk(
                                url = config.url,
                                appName = config.appName,
                                packageId = config.packageId,
                                iconUri = config.iconUri
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
                    }
                )
            }
        }
    }
}
