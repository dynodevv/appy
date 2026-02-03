package com.appy

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                val scope = rememberCoroutineScope()

                HomeScreen(
                    buildState = buildState,
                    onBuildClick = { url ->
                        scope.launch {
                            buildState = BuildState.Building(0f, "Starting...")

                            apkProcessor.generateApk(url).collect { result ->
                                when (result) {
                                    is ApkProcessingResult.Progress -> {
                                        buildState = BuildState.Building(
                                            result.progress,
                                            result.message
                                        )
                                    }
                                    is ApkProcessingResult.Success -> {
                                        buildState = BuildState.Success
                                        Toast.makeText(
                                            this@MainActivity,
                                            "APK saved to: ${result.outputPath}",
                                            Toast.LENGTH_LONG
                                        ).show()
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
